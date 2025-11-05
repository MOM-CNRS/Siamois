package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptAltLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.VocabularyLabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service to manage labels for concepts and vocabularies.
 * This service provides methods to find, update, and create labels for concepts and vocabularies.
 * It handles the retrieval of labels based on language codes and ensures that default labels are created when necessary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabelService {

    private final VocabularyLabelRepository vocabularyLabelRepository;
    private final ConceptLabelRepository conceptLabelRepository;

    /**
     * Finds the label for a given vocabulary in the specified language.
     *
     * @param vocabulary the vocabulary to find the label for
     * @param langCode   the language code for the label
     * @return the found or default label
     */
    public VocabularyLabel findLabelOf(Vocabulary vocabulary, String langCode) {
        if (vocabulary == null) {
            VocabularyLabel label = new VocabularyLabel();
            label.setValue("NULL");
            return label;
        }

        Optional<VocabularyLabel> label = vocabularyLabelRepository.findByVocabularyAndLangCode(vocabulary, langCode);
        if (label.isPresent())
            return label.get();

        List<VocabularyLabel> allLabels = vocabularyLabelRepository.findAllByVocabulary(vocabulary);
        if (allLabels.isEmpty()) {
            VocabularyLabel defaultLabel = new VocabularyLabel();
            defaultLabel.setLangCode(langCode);
            defaultLabel.setVocabulary(vocabulary);
            defaultLabel.setValue(vocabulary.getExternalVocabularyId());

            return defaultLabel;
        }

        return allLabels.get(0);
    }

    /**
     * Updates or creates a label for a concept in the specified language.
     *
     * @param concept  the concept to update the label for
     * @param langCode the language code for the label
     * @param label    the label of the label
     */
    public void updateLabel(Concept concept, String langCode, String label, Concept fieldParentConcept) {
        Optional<ConceptPrefLabel> optPrefLabel = conceptLabelRepository.findByConceptAndLangCode(concept, langCode);
        ConceptPrefLabel prefLabel;
        if (optPrefLabel.isEmpty()) {
            prefLabel = new ConceptPrefLabel();
            prefLabel.setConcept(concept);
            prefLabel.setLangCode(langCode);
        } else {
            prefLabel = optPrefLabel.get();
        }
        prefLabel.setLabel(label);
        if (fieldParentConcept != null && !fieldParentConcept.equals(concept)) {
            prefLabel.setParentConcept(fieldParentConcept);
        }
        conceptLabelRepository.save(prefLabel);
    }

    /**
     * Updates or creates a label for a vocabulary in the specified language.
     *
     * @param vocabulary the vocabulary to update the label for
     * @param langCode   the language code for the label
     * @param value      the value of the label
     */
    public void updateLabel(Vocabulary vocabulary, String langCode, String value) {
        Optional<VocabularyLabel> existingLabelOpt = vocabularyLabelRepository.findByVocabularyAndLangCode(vocabulary, langCode);
        if (existingLabelOpt.isEmpty()) {
            VocabularyLabel label = new VocabularyLabel();
            label.setLangCode(langCode);
            label.setValue(value);
            label.setVocabulary(vocabulary);
            vocabularyLabelRepository.save(label);
            return;
        }

        VocabularyLabel existingLabel = existingLabelOpt.get();

        if (existingLabel.getValue() == null || !existingLabel.getValue().equals(value)) {
            existingLabel.setValue(value);
            vocabularyLabelRepository.save(existingLabel);
        }

    }

    /**
     * Finds concepts matching the input label under the specified parent concept and language.
     * When the input is null or empty, it returns all concepts under the parent concept in the specified language.
     * Search is done by exact match and by similarity.
     * If no results are found, it falls back to searching without language restriction.
     * The results contain one concept per language, prioritizing the preferred language.
     *
     * @param parentConcept The concept of the generic field
     * @param langCode      The language code
     * @param input         The input label to search for
     * @return List of unique concepts matching the input
     */
    @Transactional(readOnly = true)
    public List<ConceptLabel> findMatchingConcepts(Concept parentConcept, String langCode, String input, int limit) {
        if (input == null || input.isEmpty()) {
            return conceptLabelRepository.findAllLabelsByParentConceptAndLangCode(parentConcept.getId(), langCode, limit);
        } else {
            return conceptLabelRepository.findAllByParentConceptAndInputLimited(parentConcept.getId(), langCode, input, limit);
        }

    }

    public void updateAltLabel(Concept savedConcept, String lang, String value, Concept fieldParentConcept) {
        Optional<ConceptAltLabel> opt = conceptLabelRepository.findAltLabelByConceptAndLangCode(savedConcept, lang);
        ConceptAltLabel altLabel;
        if (opt.isPresent()) {
            altLabel = opt.get();
        } else {
            altLabel = new ConceptAltLabel();
            altLabel.setConcept(savedConcept);
            altLabel.setLangCode(lang);
        }
        altLabel.setLabel(value);
        if (fieldParentConcept != null && !fieldParentConcept.equals(savedConcept)) {
            altLabel.setParentConcept(fieldParentConcept);
        }
        conceptLabelRepository.save(altLabel);
    }

    public ConceptLabel findLabelOf(@NonNull Concept concept, @NonNull String langCode) {
        Optional<ConceptPrefLabel> opt = conceptLabelRepository.findPrefLabelByLangCodeAndConcept(langCode, concept);
        if (opt.isPresent()) return opt.get();

        Set<ConceptAltLabel> altLabels = conceptLabelRepository.findAllAltLabelsByLangCodeAndConcept(langCode, concept);
        for (ConceptAltLabel altLabel : altLabels) {
            if (altLabel.getLangCode().equalsIgnoreCase(langCode)) {
                return altLabel;
            }
        }

        ConceptPrefLabel fallbackLabel = new ConceptPrefLabel();
        fallbackLabel.setConcept(concept);
        fallbackLabel.setLangCode(langCode);
        fallbackLabel.setLabel("[" + concept.getExternalId() + "]");
        return fallbackLabel;
    }

}
