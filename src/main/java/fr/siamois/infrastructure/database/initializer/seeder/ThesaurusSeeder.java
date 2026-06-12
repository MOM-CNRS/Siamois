package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.exceptions.api.InvalidEndpointException;
import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.vocabulary.VocabularyService;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class ThesaurusSeeder {
    private final VocabularyRepository vocabularyRepository;
    private final VocabularyService vocabularyService;

    public record ThesaurusSpec(String baseUri, String externalId) {
    }

    public Vocabulary findVocabularyOrReturnNull(String baseUri, String externalId) {
        return vocabularyRepository
                .findVocabularyByBaseUriAndVocabExternalId(baseUri, externalId)
                .orElse(null);
    }

    private Vocabulary findOrCreateVocabulary(String baseUri, String externalId) throws DatabaseDataInitException {
        Vocabulary vocab = findVocabularyOrReturnNull(baseUri, externalId);
        if (vocab != null) return vocab;
        String fullUri = baseUri + "?idt=" + externalId;
        try {
            return vocabularyService.findOrCreateVocabularyOfUri(fullUri);
        } catch (InvalidEndpointException e) {
            throw new DatabaseDataInitException("Error creating vocabulary from URI: " + fullUri, e);
        }
    }

    public Map<String, Vocabulary> seed(List<ThesaurusSpec> specs) throws DatabaseDataInitException {
        Map<String, Vocabulary> result = new HashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                result.put(s.externalId(), findOrCreateVocabulary(s.baseUri(), s.externalId()));
            } catch (DatabaseDataInitException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Thésaurus ligne " + (i + 1) + "] '" + s.externalId() + "' : " + e.getMessage(), e);
            }
        }
        return result;
    }
}
