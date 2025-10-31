package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConceptSeeder {
    private final ConceptRepository conceptRepo;
    private final LocalizedConceptDataRepository localizedConceptDataRepository;
    private final ConceptLabelRepository conceptLabelRepository;

    public record ConceptKey(String vocabularyExtId, String conceptExtId) {}

    public Concept findConceptOrReturnNull(String vocabularyId, String externalId) {
        return conceptRepo.findConceptByExternalIdIgnoreCase(vocabularyId, externalId)
                .orElse(null);
    }

    private void saveLabel(Concept concept, String label, String lang) {
        Optional<ConceptPrefLabel> opt = conceptLabelRepository.findPrefLabelByLangCodeAndConcept(lang, concept);
        if (opt.isEmpty()) {
            ConceptPrefLabel prefLabel = new ConceptPrefLabel();
            prefLabel.setConcept(concept);
            prefLabel.setLangCode(lang);
            prefLabel.setLabel(label);
            conceptLabelRepository.save(prefLabel);
        }
    }

    public Concept findConceptOrReturnNull(ConceptKey key) {
        return conceptRepo
                .findConceptByExternalIdIgnoreCase(key.vocabularyExtId(), key.conceptExtId())
                .orElse(null);
    }

    public Concept findConceptOrThrow(ConceptKey key) {
        Concept c = findConceptOrReturnNull(key);
        if(c == null) {
            throw new IllegalStateException("Concept introuvable");
        }
        return c;
    }

    public record ConceptSpec(String vocabularyId, String externalId, String label, String lang) {}
    public void seed(Vocabulary vocab, List<ConceptSpec> specs) {
        for (var s : specs) {
            Concept concept = findConceptOrReturnNull(s.vocabularyId(), s.externalId());
            if(concept == null) {
                var c = new Concept();
                c.setExternalId(s.externalId());
                c.setVocabulary(vocab);
                concept = conceptRepo.save(c);
            }
            saveLabel(concept, s.label, s.lang);

        }
    }
}
