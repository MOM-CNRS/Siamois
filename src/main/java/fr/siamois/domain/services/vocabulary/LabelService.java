package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.models.vocabulary.label.VocabularyLabel;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.LocalizedConceptDataRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.label.VocabularyLabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service to manage labels for concepts and vocabularies.
 * This service provides methods to find, update, and create labels for concepts and vocabularies.
 * It handles the retrieval of labels based on language codes and ensures that default labels are created when necessary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabelService {

    public static final double MIN_SIMILARITY_SCORE = 0.6;
    private final VocabularyLabelRepository vocabularyLabelRepository;
    private final LocalizedConceptDataRepository localizedConceptDataRepository;

    /**
     * Finds the label for a given concept in the specified language.
     * If no label exists, it returns a default label with the concept's external ID.
     *
     * @param concept  the concept to find the label for
     * @param langCode the language code for the label
     * @return the found or default label
     */
    @Transactional(readOnly = true)
    public LocalizedConceptData findLabelOf(Concept concept, String langCode) {
        if (concept == null) {
            return null;
        }
        Optional<LocalizedConceptData> optLocalized = localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), langCode);
        return optLocalized.orElse(null);
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
     * @param label    the label of the label
     */
    public void updateLabel(Concept concept, String langCode, String label, Concept fieldParentConcept) {
        Optional<LocalizedConceptData> conceptData = localizedConceptDataRepository.findByConceptAndLangCode(concept.getId(), langCode);
        LocalizedConceptData savedConceptData = null;
        if (conceptData.isEmpty()) {
            savedConceptData = new LocalizedConceptData();
            savedConceptData.setConcept(concept);
            savedConceptData.setLangCode(langCode);
            savedConceptData.setLabel(label);
            savedConceptData.setParentConcept(fieldParentConcept);
        } else {
            savedConceptData = conceptData.get();
            savedConceptData.setLabel(label);
        }
        localizedConceptDataRepository.save(savedConceptData);
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

    @Transactional(readOnly = true)
    protected List<Concept> findAllConcepts(Concept parentConcept, int limit) {
        try {
            return localizedConceptDataRepository.findAllByParentConcept(parentConcept, Limit.of(limit))
                    .stream()
                    .map(LocalizedConceptData::getConcept)
                    .toList();
        } catch (RuntimeException e) {
            log.error(e.getMessage());
            return List.of();
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
    public List<Concept> findMatchingConcepts(Concept parentConcept, String langCode, String input, int limit) {
        Set<Concept> results = new HashSet<>();
        if (input == null || input.isEmpty()) {
            results.addAll(findAllConcepts(parentConcept, limit));
        } else {
            results.addAll(localizedConceptDataRepository
                    .findConceptByFieldCodeAndInputLimit(parentConcept.getId(), langCode, input, MIN_SIMILARITY_SCORE, limit)
                    .stream()
                    .map(LocalizedConceptData::getConcept)
                    .toList());

            Set<LocalizedConceptData> exactMatchOtherLang = localizedConceptDataRepository.findLocalizedConceptDataByParentConceptAndLabelContaining(parentConcept, input);
            for (LocalizedConceptData lcd : exactMatchOtherLang) {
                if (results.size() < limit) {
                    results.add(lcd.getConcept());
                } else {
                    break;
                }
            }
        }

        return results
                .stream()
                .toList();
    }

}
