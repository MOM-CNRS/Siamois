package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.ConceptLabelRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.VocabularyLabelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service to manage labels for concepts and vocabularies.
 * This service provides methods to find, update, and create labels for concepts and vocabularies.
 * It handles the retrieval of labels based on language codes and ensures that default labels are created when necessary.
 */
@Service
public class LabelService {

    private final ConceptLabelRepository conceptLabelRepository;
    private final VocabularyLabelRepository vocabularyLabelRepository;

    public LabelService(ConceptLabelRepository conceptLabelRepository, VocabularyLabelRepository vocabularyLabelRepository) {
        this.conceptLabelRepository = conceptLabelRepository;
        this.vocabularyLabelRepository = vocabularyLabelRepository;
    }

    /**
     * Finds the label for a given concept in the specified language.
     * If no label exists, it returns a default label with the concept's external ID.
     *
     * @param concept  the concept to find the label for
     * @param langCode the language code for the label
     * @return the found or default label
     */
    @Transactional(readOnly = true)
    public ConceptLabel findLabelOf(Concept concept, String langCode) {
        if (concept == null) {
            ConceptLabel label = new ConceptLabel();
            label.setValue("NULL");
            return label;
        }

        Optional<ConceptLabel> label = conceptLabelRepository.findByConceptAndLangCode(concept, langCode);
        if (label.isPresent())
            return label.get();

        List<ConceptLabel> allLabels = conceptLabelRepository.findAllByConcept(concept);
        if (allLabels.isEmpty()) {
            ConceptLabel defaultLabel = new ConceptLabel();
            defaultLabel.setLangCode(langCode);
            defaultLabel.setConcept(concept);
            defaultLabel.setValue(concept.getExternalId());

            return defaultLabel;
        }

        return allLabels.get(0);
    }

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
     * @param value    the value of the label
     */
    public void updateLabel(Concept concept, String langCode, String value) {
        Optional<ConceptLabel> existingLabelOpt = conceptLabelRepository.findByConceptAndLangCode(concept, langCode);
        if (existingLabelOpt.isEmpty()) {
            ConceptLabel label = new ConceptLabel();
            label.setLangCode(langCode);
            label.setValue(value);
            label.setConcept(concept);
            conceptLabelRepository.save(label);
            return;
        }

        ConceptLabel existingLabel = existingLabelOpt.get();

        if (existingLabel.getValue() == null || !existingLabel.getValue().equals(value)) {
            existingLabel.setValue(value);
            conceptLabelRepository.save(existingLabel);
        }

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

}
