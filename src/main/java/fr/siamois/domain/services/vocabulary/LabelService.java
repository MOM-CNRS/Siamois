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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

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
    @NonNull
    public VocabularyLabel findLabelOf(@Nullable Vocabulary vocabulary, @NonNull String langCode) {
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
     * Updates or creates a preferred label for a concept in the specified language.
     *
     * @param concept            the concept to update the label for
     * @param langCode           the language code for the label
     * @param label              the value of the label
     * @param fieldParentConcept the parent concept of the field, can be null
     */
    public void updateLabel(@NonNull Concept concept, @NonNull String langCode, @NonNull String label, @Nullable Concept fieldParentConcept) {
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
    public void updateLabel(@NonNull Vocabulary vocabulary, @NonNull String langCode, @NonNull String value) {
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
     * Updates or creates an alternative label for a concept in the specified language.
     *
     * @param savedConcept       the concept to update the alt label for
     * @param lang               the language code for the alt label
     * @param value              the value of the alt label
     * @param fieldParentConcept the parent concept of the field, can be null
     */
    public void updateAltLabel(@NonNull Concept savedConcept, @NonNull String lang, @NonNull String value, @Nullable Concept fieldParentConcept) {
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

    /**
     * Finds the label (preferred or alternative) for a concept in a given language.
     * @param concept The concept
     * @param langCode The language code
     * @return This method returns the preferred label if it exists. Then looks for an alternative label in
     * the given language and return the first found. If none is found, it returns a fallback label in the format "[externalId]".
     */
    @NonNull
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

    /**
     * Finds all altLabels for a concept in a given language
     *
     * @param concept  The concept
     * @param langCode The language code
     * @return Set of the alt labels found
     */
    @NonNull
    public Set<ConceptAltLabel> findAllAltLabelOf(@NonNull Concept concept, @NonNull String langCode) {
        return conceptLabelRepository.findAllAltLabelsByLangCodeAndConcept(langCode, concept);
    }

}
