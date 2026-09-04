package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConceptSeeder {
    private final ConceptRepository conceptRepo;
    private final ConceptLabelRepository conceptLabelRepository;
    private final VocabularyRepository vocabularyRepository;

    /**
     * {@code label} is the human-readable text read from the source "X label" column, when present —
     * carried along purely for error messages (e.g. "concept not found"), never for identity: two
     * keys with the same vocabulary/concept id are equal regardless of label, so it never disturbs
     * the bulk-fetch cache lookups (`Map&lt;ConceptKey, Concept&gt;`) built from DB results, which never
     * carry a label.
     */
    public record ConceptKey(String vocabularyExtId, String conceptExtId, String label) {

        public ConceptKey(String vocabularyExtId, String conceptExtId) {
            this(vocabularyExtId, conceptExtId, null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ConceptKey other)) return false;
            return Objects.equals(vocabularyExtId, other.vocabularyExtId)
                    && Objects.equals(conceptExtId, other.conceptExtId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(vocabularyExtId, conceptExtId);
        }
    }

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
            throw new IllegalStateException("Concept "+key+" introuvable");
        }
        return c;
    }

    /**
     * User-facing message for a concept missing from a bulk-fetched cache during import — includes
     * a direct link to the concept in the institution's configured thesaurus when it can be resolved,
     * so the user can check whether it simply hasn't been loaded into Siamois yet.
     */
    public String describeMissingConcept(ConceptKey key, Long institutionId) {
        if (key == null) return "Concept manquant (aucune valeur fournie)";
        String link = buildConceptLink(key, institutionId);
        StringBuilder base = new StringBuilder("Concept non chargé dans Siamois (vocabulaire ")
                .append(key.vocabularyExtId()).append(", concept ").append(key.conceptExtId());
        if (key.label() != null && !key.label().isBlank()) {
            base.append(", libellé \"").append(key.label()).append('"');
        }
        base.append(")");
        return link != null ? base + " : " + link : base.toString();
    }

    private String buildConceptLink(ConceptKey key, Long institutionId) {
        if (institutionId == null || key.vocabularyExtId() == null) return null;
        return vocabularyRepository.findDistinctByInstitutionId(institutionId).stream()
                .filter(v -> key.vocabularyExtId().equalsIgnoreCase(v.getExternalVocabularyId()))
                .findFirst()
                .map(v -> v.getBaseUri() + "/?idc=" + key.conceptExtId() + "&idt=" + key.vocabularyExtId())
                .orElse(null);
    }

    public record ConceptSpec(String vocabularyId, String externalId, String label, String lang) {}
    public void seed(Vocabulary vocab, List<ConceptSpec> specs) {
        for (int i = 0; i < specs.size(); i++) {
            var s = specs.get(i);
            try {
                Concept concept = findConceptOrReturnNull(s.vocabularyId(), s.externalId());
                if(concept == null) {
                    var c = new Concept();
                    c.setExternalId(s.externalId());
                    c.setVocabulary(vocab);
                    concept = conceptRepo.save(c);
                }
                saveLabel(concept, s.label, s.lang);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "[Concept ligne " + (i + 1) + "] '" + s.externalId() + "' : " + e.getMessage(), e);
            }
        }
    }
}
