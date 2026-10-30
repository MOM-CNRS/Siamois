package fr.siamois.domain.services.vocabulary;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.DocumentationThesaurus;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.CustomFieldPendingCandidate;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyApiKey;
import fr.siamois.infrastructure.api.CandidateApi;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.CandidateDTO;
import fr.siamois.infrastructure.api.dto.ValueLangDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptRemoteAutocompleteDTO;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.CustomFieldPendingCandidateRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.DocumentationThesaurusRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyKeyRepository;
import fr.siamois.utils.context.ExecutionContextHolder;
import fr.siamois.utils.vocabulary.ConceptApiUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * Documents the additional fields in the thesaurus of their project (or of its organisation).
 * <p>
 * Creating a field submits a candidate named after it, its description as definition. OpenTheso refuses a
 * candidate whose name is already a concept: the thesaurus is then searched for a concept whose preferred label
 * is exactly the field's name, which becomes the field's concept, saved as a stub loaded on first access. A
 * candidate accepted, or a search finding nothing, leaves a {@link CustomFieldPendingCandidate}: the search is made
 * again each time the thesaurus is loaded, until the candidate becomes a concept.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldCandidateService {

    private final CandidateApi candidateApi;
    private final ConceptApi conceptApi;
    private final ConceptService conceptService;
    private final ConceptRepository conceptRepository;
    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldPendingCandidateRepository pendingCandidateRepository;
    private final DocumentationThesaurusRepository documentationThesaurusRepository;
    private final VocabularyKeyRepository vocabularyKeyRepository;

    /**
     * Submits a candidate for a field just created in a project, to the documentation thesaurus of the project or,
     * failing that, of the current user's organisation. A field already documented by a concept is left alone.
     * Never throws: the thesaurus being unreachable must not prevent the field's
     * creation, the candidate stays pending.
     */
    @Transactional
    public void submitCandidateFor(@NonNull CustomField field, @Nullable Long projectId) {
        if (field.getConcept() != null) return;
        UserInfo user = ExecutionContextHolder.getNonNull();
        Optional<Vocabulary> thesaurus = documentationThesaurusOf(user.getInstitution().getId(), projectId);
        if (thesaurus.isEmpty()) {
            log.debug("No documentation thesaurus for project {}: no candidate for field '{}'", projectId, field.getLabel());
            return;
        }
        Vocabulary vocabulary = thesaurus.get();

        if (!postCandidate(field, vocabulary, user)) {
            // most likely refused because a concept of that name exists: look for it
            Optional<Concept> concept = searchConcept(field, vocabulary, user.getLang());
            if (concept.isPresent()) {
                linkConcept(field, concept.get());
                return;
            }
        }
        if (pendingCandidateRepository.findByFieldIdAndVocabularyId(field.getId(), vocabulary.getId()).isEmpty()) {
            pendingCandidateRepository.save(pendingCandidate(field, vocabulary, user.getLang()));
        }
    }

    /**
     * Searches the thesaurus again for the concept of every field whose candidate is pending in it. A concept found
     * becomes the field's concept and the candidate is no longer pending. Called when the thesaurus is loaded; never
     * throws, a field whose search fails stays pending.
     */
    @Transactional
    public void synchronizePendingOf(@NonNull Vocabulary vocabulary) {
        List<CustomFieldPendingCandidate> pending = pendingCandidateRepository.findAllByVocabularyId(vocabulary.getId());
        if (pending.isEmpty()) return;
        log.debug("Synchronizing {} pending fields with thesaurus {}", pending.size(), vocabulary.getExternalVocabularyId());
        for (CustomFieldPendingCandidate candidate : pending) {
            CustomField field = candidate.getField();
            if (field.getConcept() != null) {
                pendingCandidateRepository.delete(candidate);
                continue;
            }
            searchConcept(field, vocabulary, candidate.getLang()).ifPresent(concept -> {
                linkConcept(field, concept);
                pendingCandidateRepository.delete(candidate);
            });
        }
    }

    /**
     * The concept of a field, fully loaded: a concept linked as a stub is fetched from the thesaurus on this first
     * access.
     */
    @Transactional
    public Optional<Concept> findConceptOf(@NonNull CustomField field) {
        Optional<Concept> concept = Optional.ofNullable(field.getConcept());
        concept.ifPresent(conceptService::loadIfUnloaded);
        return concept;
    }

    /**
     * Gives a field replacing another one (a type change) the concept of the field it replaces, or its pending
     * candidates.
     */
    @Transactional
    public void transfer(@NonNull CustomField from, @NonNull CustomField to) {
        if (to.getConcept() != null) return;
        if (from.getConcept() != null) {
            linkConcept(to, from.getConcept());
            return;
        }
        for (CustomFieldPendingCandidate candidate : pendingCandidateRepository.findAllByFieldId(from.getId())) {
            if (pendingCandidateRepository.findByFieldIdAndVocabularyId(to.getId(), candidate.getVocabulary().getId()).isEmpty()) {
                pendingCandidateRepository.save(pendingCandidate(to, candidate.getVocabulary(), candidate.getLang()));
            }
        }
    }

    /** Removes the pending candidates of a field about to be deleted. */
    @Transactional
    public void deletePendingCandidatesOf(@NonNull CustomField field) {
        pendingCandidateRepository.deleteAll(pendingCandidateRepository.findAllByFieldId(field.getId()));
    }

    private void linkConcept(CustomField field, Concept concept) {
        field.setConcept(concept);
        customFieldRepository.save(field);
    }

    private static CustomFieldPendingCandidate pendingCandidate(CustomField field, Vocabulary vocabulary, String lang) {
        CustomFieldPendingCandidate candidate = new CustomFieldPendingCandidate();
        candidate.setField(field);
        candidate.setVocabulary(vocabulary);
        candidate.setLang(lang);
        return candidate;
    }

    private Optional<Vocabulary> documentationThesaurusOf(Long institutionId, @Nullable Long projectId) {
        Optional<DocumentationThesaurus> thesaurus = projectId == null
                ? Optional.empty()
                : documentationThesaurusRepository.findByActionUnitId(projectId);
        if (thesaurus.isEmpty()) {
            thesaurus = documentationThesaurusRepository.findOfInstitution(institutionId);
        }
        return thesaurus.map(DocumentationThesaurus::getVocabulary);
    }

    /** @return whether the thesaurus accepted the candidate; false as well when the user has no API key for it */
    private boolean postCandidate(CustomField field, Vocabulary vocabulary, UserInfo user) {
        VocabularyApiKey.VocabularyApiKeyId keyId = new VocabularyApiKey.VocabularyApiKeyId();
        keyId.setInstanceId(vocabulary.getInstance().getId());
        keyId.setPersonId(user.getUser().getId());
        Optional<VocabularyApiKey> key = vocabularyKeyRepository.findById(keyId);
        if (key.isEmpty()) {
            log.debug("User {} has no API key for thesaurus {}: no candidate submitted",
                    user.getUser().getId(), vocabulary.getExternalVocabularyId());
            return false;
        }
        try {
            return candidateApi.postCandidateFor(key.get(), candidateOf(field, vocabulary, user.getLang()));
        } catch (JsonProcessingException | IllegalStateException e) {
            // IllegalStateException: the key no longer decrypts
            log.error("Could not submit the candidate of field '{}' to thesaurus {}", field.getLabel(),
                    vocabulary.getExternalVocabularyId(), e);
            return false;
        }
    }

    private CandidateDTO candidateOf(CustomField field, Vocabulary vocabulary, String lang) {
        CandidateDTO candidate = new CandidateDTO();
        candidate.setThesoId(vocabulary.getExternalVocabularyId());
        candidate.setTerme(new ValueLangDTO[]{valueLang(field.getLabel(), lang)});
        if (StringUtils.isNotBlank(field.getHint())) {
            candidate.setDefinition(new ValueLangDTO[]{valueLang(field.getHint(), lang)});
        }
        return candidate;
    }

    private static ValueLangDTO valueLang(String value, String lang) {
        ValueLangDTO valueLang = new ValueLangDTO();
        valueLang.setValue(value);
        valueLang.setLang(lang);
        return valueLang;
    }

    /** The concept whose preferred label is exactly the field's name, if the thesaurus has one. */
    private Optional<Concept> searchConcept(CustomField field, Vocabulary vocabulary, String lang) {
        String label = field.getLabel();
        if (StringUtils.isBlank(label)) return Optional.empty();
        List<ConceptRemoteAutocompleteDTO> results;
        try {
            results = conceptApi.fetchRemoteAutocomplete(vocabulary.getBaseUri(), vocabulary.getExternalVocabularyId(),
                    label, lang);
        } catch (JsonProcessingException | RestClientException e) {
            log.warn("Could not search thesaurus {} for '{}', the field stays pending: {}",
                    vocabulary.getExternalVocabularyId(), label, e.getMessage());
            return Optional.empty();
        }
        if (results == null) return Optional.empty();
        return results.stream()
                .filter(result -> !Boolean.TRUE.equals(result.isAltLabel()))
                .filter(result -> result.label() != null && label.trim().equals(result.label().trim()))
                .filter(result -> StringUtils.isNotBlank(result.uri()))
                .findFirst()
                .map(result -> findOrCreateStub(vocabulary, result));
    }

    /**
     * The local concept of a search result, or a stub of it: its vocabulary, URI and external id, not loaded.
     * The rest is fetched on first access, see {@link ConceptService#loadIfUnloaded(Concept)}.
     */
    private Concept findOrCreateStub(Vocabulary vocabulary, ConceptRemoteAutocompleteDTO result) {
        String externalId = result.identifier() != null
                ? String.valueOf(result.identifier())
                : ConceptApiUtils.externalIdFromUri(result.uri());
        Optional<Concept> existing = externalId == null
                ? conceptRepository.findByUri(result.uri())
                : conceptRepository.findConceptByExternalIdIgnoreCase(vocabulary.getExternalVocabularyId(), externalId);
        return existing.orElseGet(() -> {
            Concept stub = new Concept();
            stub.setVocabulary(vocabulary);
            stub.setUri(result.uri());
            stub.setExternalId(externalId);
            stub.setLoaded(false);
            return conceptRepository.save(stub);
        });
    }
}
