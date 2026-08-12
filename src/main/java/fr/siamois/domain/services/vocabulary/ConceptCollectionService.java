package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.exceptions.ErrorProcessingExpansionException;
import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.vocabulary.VocabularyNotFoundException;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.ConceptApi;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptApiCollectionDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptBranchDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptCollectionDetachedDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository;
import fr.siamois.utils.context.ExecutionContextHolder;
import fr.siamois.utils.vocabulary.ConceptApiUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static fr.siamois.utils.ArkUtils.extractArkOf;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConceptCollectionService {

    private final ConceptCollectionRepository conceptCollectionRepository;
    private final VocabularyService vocabularyService;
    private final ConceptApi conceptApi;
    private final ApplicationContext applicationContext;


    /**
     * Creates the JPA ConceptCollection or retrieve it and load all concepts associated
     * @param collection The transient collection
     */
    @Transactional
    public ConceptCollection createOrUpdateConceptCollection(@NonNull ConceptCollectionDTO collection) {
        try {
            Vocabulary vocabulary = vocabularyService.findOrCreateVocabularyOfUri(collection.getVocabulary().completeUri());
            Optional<ConceptCollection> opt = conceptCollectionRepository.findByVocabularyAndExternalId(vocabulary, collection.getExternalId());
            ConceptCollection savedCollection;
            if(opt.isPresent()) {
                savedCollection = opt.get();
            } else {
                savedCollection = new ConceptCollection();
                savedCollection.setExternalId(collection.getExternalId());
                savedCollection.setVocabulary(vocabulary);
                savedCollection = conceptCollectionRepository.save(savedCollection);
            }
            saveAllThesaurusInfoOfCollection(savedCollection);
            return savedCollection;
        } catch (InvalidEndpointException e) {
            log.error("Vocabulary could not be found", e);
            throw new VocabularyNotFoundException("Vocabulary could not be found");
        }
    }

    private void saveAllThesaurusInfoOfCollection(@NonNull ConceptCollection savedCollection) {
        ConceptBranchDTO branchDTO;
        try {
            branchDTO = conceptApi.fetchCollectionBranch(savedCollection.getVocabulary(), savedCollection);
        } catch (ErrorProcessingExpansionException e) {
            log.error("Error while fetching collection branch", e);
            return;
        }
        if (Objects.isNull(branchDTO)) {
            return;
        }
        ConceptApiUtils.BranchLoadComponents components = new ConceptApiUtils.BranchLoadComponents(applicationContext);
        Map<String, Concept> savedConcepts = ConceptApiUtils.saveAllConceptsOfBranch(components, savedCollection.getVocabulary(), branchDTO);
        for (Concept concept : savedConcepts.values()) {
            savedCollection.getConcepts().add(concept);
        }
        conceptCollectionRepository.save(savedCollection);
    }

    /**
     * Lists the public collections of a remote thesaurus, so one of them can be picked to configure a
     * field on it. Nothing is imported : the returned collections hold no local id and no concept, only
     * what identifies them and the label to display.
     *
     * @param vocabularyDTO the remote vocabulary to list the collections of
     * @return the collections of that thesaurus, labelled in the language of the current user
     */
    @NonNull
    public List<ConceptCollectionDetachedDTO> fetchCollectionsFromRemoteThesaurus(VocabularyDTO vocabularyDTO) {
        List<ConceptApiCollectionDTO> collections = conceptApi.fetchPublicCollections(vocabularyDTO);
        UserInfo info = ExecutionContextHolder.get();
        String lang = Objects.isNull(info) ? null : info.getLang();

        return collections.stream()
                .map(collection -> new ConceptCollectionDetachedDTO(
                        collection.idGroup(),
                        vocabularyDTO,
                        labelToDisplay(collection, lang),
                        labelsOf(collection)))
                .toList();
    }

    @NonNull
    public Optional<ConceptCollectionDetachedDTO> fetchCollectionDesignatedBy(@NonNull VocabularyDTO vocabularyDTO, @NonNull String uri) {
        try {
            Optional<String> idGroup = groupIdOf(vocabularyDTO, uri);
            if (idGroup.isEmpty()) {
                return Optional.empty();
            }
            return fetchCollectionsFromRemoteThesaurus(vocabularyDTO).stream()
                    .filter(collection -> collection.getExternalId().equalsIgnoreCase(idGroup.get()))
                    .findFirst();
        } catch (RuntimeException e) {
            log.warn("Could not read the collection designated by {} on {}", uri, vocabularyDTO.getBaseUri(), e);
            return Optional.empty();
        }
    }

    @NonNull
    private Optional<String> groupIdOf(@NonNull VocabularyDTO vocabularyDTO, @NonNull String uri) {
        String idGroup = UriComponentsBuilder.fromUriString(uri).build().getQueryParams().getFirst("idg");
        if (idGroup != null && !idGroup.isBlank()) {
            return Optional.of(idGroup);
        }
        int arkIndex = uri.indexOf("ark:");
        if (arkIndex < 0) {
            return Optional.empty();
        }
        return conceptApi.fetchGroupIdOfArk(vocabularyDTO.getBaseUri(), arkOf(uri.substring(arkIndex)));
    }

    /**
     * The ark alone : what follows it in the URL — a query string, a fragment — is not part of the
     * identifier and would be sent to the API as if it were.
     */
    @NonNull
    private static String arkOf(@NonNull String arkAndWhatFollows) {
        return extractArkOf(arkAndWhatFollows);
    }

    /**
     * The label of a collection in the given language, falling back to the first label available
     * suffixed with its own language, and finally to the id of the collection when it has no label.
     * This is the same resolution as the {@code concept_get_label} database function.
     *
     * @param collection the collection to label
     * @param lang       the language to look for, null when no user context is bound
     * @return the label to display, never null
     */
    @NonNull
    private static String labelToDisplay(@NonNull ConceptApiCollectionDTO collection, @Nullable String lang) {
        List<LabelDTO> labels = labelsOf(collection);
        return labels.stream()
                .filter(label -> Objects.nonNull(lang) && lang.equalsIgnoreCase(label.getLang()))
                .map(LabelDTO::getTitle)
                .findFirst()
                .orElseGet(() -> labels.stream()
                        .findFirst()
                        .map(label -> String.format("%s (%s)", label.getTitle(), label.getLang()))
                        .orElse(collection.idGroup()));
    }

    @NonNull
    private static List<LabelDTO> labelsOf(@NonNull ConceptApiCollectionDTO collection) {
        return Objects.isNull(collection.labels()) ? List.of() : collection.labels();
    }

}
