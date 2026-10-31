package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.vocabulary.VocabularyNotFoundException;
import fr.siamois.domain.models.vocabulary.ThesaurusInfo;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.VocabularyType;
import fr.siamois.infrastructure.api.ThesaurusApi;
import fr.siamois.infrastructure.api.dto.LabelDTO;
import fr.siamois.infrastructure.api.dto.ThesaurusDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service to manage Vocabulary
 *
 * @author Julien Linget
 */
@Service
@RequiredArgsConstructor
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;
    private final ThesaurusApi thesaurusApi;
    private final VocabularyTypeRepository vocabularyTypeRepository;
    private final LabelService labelService;
    private final VocabularyInstanceService vocabularyInstanceService;

    /**
     * Find a action unit by its ID
     *
     * @param id The ID to fetch
     * @return The Vocabulary having the given ID
     * @throws VocabularyNotFoundException If no vocabulary is found for the given id
     */
    public Vocabulary findVocabularyById(long id) {
        return this.vocabularyRepository.findById(id).orElseThrow(() -> new VocabularyNotFoundException("Vocabulary not found with ID: " + id));
    }

    /**
     * Tous les vocabulaires enregistrés en base.
     */
    public List<Vocabulary> findAll() {
        return vocabularyRepository.findAllOrderById();
    }

    /**
     * Vocabulaires liés à une organisation via {@code concept_field_config}.
     */
    public List<Vocabulary> findAllByInstitutionId(long institutionId) {
        return vocabularyRepository.findDistinctByInstitutionId(institutionId);
    }

    /**
     * Save a vocabulary if it does not exist, or return the existing one.
     *
     * @param vocabulary The vocabulary to save or retrieve
     * @return The saved or existing vocabulary
     */
    public Vocabulary saveOrGetVocabulary(Vocabulary vocabulary) {
        Optional<Vocabulary> vocabOpt = vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(vocabulary.getBaseUri(), vocabulary.getExternalVocabularyId());
        return vocabOpt.orElseGet(() -> {
            vocabulary.setInstance(vocabularyInstanceService.findOrCreate(vocabulary.getBaseUri()));
            return vocabularyRepository.save(vocabulary);
        });
    }
    public String resolveRedirections(String uri) {
        return thesaurusApi.resolveRedirections(uri);
    }

    /**
     * Find or create a vocabulary based on the given URI.
     *
     * @param uri The URI of the vocabulary to find or create.
     * @return The found or created Vocabulary.
     * @throws InvalidEndpointException If the endpoint is invalid or cannot be reached.
     */
    public Vocabulary findOrCreateVocabularyOfUri(String uri) throws InvalidEndpointException {
        ThesaurusDTO thesaurus = thesaurusApi.fetchThesaurusInfo(uri);

        VocabularyType type = vocabularyTypeRepository.findVocabularyTypeByLabel("Thesaurus").orElseThrow(() -> new IllegalStateException("Thesaurus type not found"));

        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setExternalVocabularyId(thesaurus.getIdTheso());
        vocabulary.setBaseUri(thesaurus.getBaseUri());
        vocabulary.setType(type);

        Vocabulary savedVocabulary = saveOrGetVocabulary(vocabulary);

        for (LabelDTO label : thesaurus.getLabels()) {
            labelService.updateLabel(savedVocabulary, label.getLang(), label.getTitle());
        }

        return savedVocabulary;
    }

    /** The public thesauri of an OpenTheso instance that are meant for Siamois. */
    public List<ThesaurusInfo> findAllPublicThesaurusOf(@NonNull String server, @NonNull String labelLang) throws InvalidEndpointException {
        return listPublicThesauri(server, labelLang, true);
    }

    /** Every public thesaurus of an OpenTheso instance, whatever its type. */
    public List<ThesaurusInfo> findAllThesaurusOf(@NonNull String server, @NonNull String labelLang) throws InvalidEndpointException {
        return listPublicThesauri(server, labelLang, false);
    }

    private List<ThesaurusInfo> listPublicThesauri(String server, String labelLang, boolean siamoisOnly) throws InvalidEndpointException {
        List<ThesaurusDTO> dtos = thesaurusApi.fetchAllPublicThesaurus(server);
        List<ThesaurusInfo> results = new ArrayList<>();
        for (ThesaurusDTO dto : dtos) {
            if (siamoisOnly && (dto.getType() == null || !dto.getType().strip().equalsIgnoreCase("Siamois"))) {
                continue;
            }
            String label;
            String lang;
            if (!dto.getLabels().isEmpty()) {
                label = dto.getLabels().get(0).getTitle();
                lang = dto.getLabels().get(0).getLang();
                int i = 0;
                while (i < dto.getLabels().size() && !dto.getLabels().get(i).getLang().equals(labelLang)) i++;
                if (i < dto.getLabels().size()) {
                    label = dto.getLabels().get(i).getTitle();
                    lang = dto.getLabels().get(i).getLang();
                }
            } else {
                label = "NULL";
                lang = labelLang;
            }
            results.add(new ThesaurusInfo(server, dto.getIdTheso(), label, lang));
        }
        return results;
    }

}
