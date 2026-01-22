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
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service to manage Vocabulary
 *
 * @author Julien Linget
 */
@Service
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;
    private final ThesaurusApi thesaurusApi;
    private final VocabularyTypeRepository vocabularyTypeRepository;
    private final LabelService labelService;

    public VocabularyService(VocabularyRepository vocabularyRepository, ThesaurusApi thesaurusApi, VocabularyTypeRepository vocabularyTypeRepository, LabelService labelService) {
        this.vocabularyRepository = vocabularyRepository;
        this.thesaurusApi = thesaurusApi;
        this.vocabularyTypeRepository = vocabularyTypeRepository;
        this.labelService = labelService;
    }

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
     * Save a vocabulary if it does not exist, or return the existing one.
     *
     * @param vocabulary The vocabulary to save or retrieve
     * @return The saved or existing vocabulary
     */
    public Vocabulary saveOrGetVocabulary(Vocabulary vocabulary) {
        Optional<Vocabulary> vocabOpt = vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(vocabulary.getBaseUri(), vocabulary.getExternalVocabularyId());
        return vocabOpt.orElseGet(() -> vocabularyRepository.save(vocabulary));
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

    public List<ThesaurusInfo> findAllPublicThesaurusOf(@NonNull String server, @NonNull String labelLang) throws InvalidEndpointException {
        List<ThesaurusDTO> dtos = thesaurusApi.fetchAllPublicThesaurus(server);
        List<ThesaurusInfo> results = new ArrayList<>();
        for (ThesaurusDTO dto : dtos) {
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
