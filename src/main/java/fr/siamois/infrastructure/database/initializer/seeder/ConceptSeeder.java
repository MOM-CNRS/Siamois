package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConceptSeeder {
    private final ConceptRepository conceptRepo;

    public record ConceptKey(String vocabularyExtId, String conceptExtId) {}

    public Concept findConceptOrReturnNull(String vocabularyId, String externalId) {
        return conceptRepo.findConceptByExternalIdIgnoreCase(vocabularyId, externalId)
                .orElse(null);
    }

//    public ConceptLabel findConceptLabelOrReturnNull(Concept concept, String lang) {
//        return labelRepo.findByConceptAndLangCode(concept, lang)
//                .orElse(null);
//    }

//    private void saveLabel(Concept concept, String label, String lang) {
//        var l = new ConceptLabel();
//        l.setConcept(concept);
//        l.setValue(label);
//        l.setLangCode(lang);
//        labelRepo.save(l);
//    }

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
                // saveLabel(concept, s.label, s.lang);
            }
//            else {
//                ConceptLabel label = findConceptLabelOrReturnNull(concept, s.lang());
//                if (label == null) {
//                    saveLabel(concept, s.label, s.lang);
//                }
//            }

        }
    }
}
