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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    private final VocabularyLabelRepository vocabularyLabelRepository;
    private final LocalizedConceptDataRepository localizedConceptDataRepository;
    private final ConceptRepository conceptRepository;

    private static final double SIMILARITY_MIN_SCORE = 0.4;

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
    protected List<Concept> findAllConcepts(Concept parentConcept, String langCode) {
        try {
            Map<Concept, List<LocalizedConceptData>> result = new HashMap<>();
            Long parentId = parentConcept.getId();
            fillData(result, localizedConceptDataRepository.findAllByParentConceptAndLangCode(parentId, langCode));
            if (result.isEmpty()) {
                fillData(result, localizedConceptDataRepository.findAllByParentConcept(parentConcept));
            }

            return oneLangForEachConcept(result, langCode);
        } catch (RuntimeException e) {
            log.error(e.getMessage());
            return List.of();
        }
    }

    @Transactional
    protected void fillData(Map<Concept, List<LocalizedConceptData>> sortedMap, Iterable<LocalizedConceptData> dataSet) {
        for (LocalizedConceptData data : dataSet) {
            Concept savedConcept = conceptRepository.findByExternalId(data.getConcept().getExternalId()).orElseThrow(() -> new IllegalStateException("Concept should be in the database not found"));
            sortedMap.putIfAbsent(savedConcept, new ArrayList<>());
            sortedMap.get(data.getConcept()).add(data);
        }
    }

    private List<Concept> oneLangForEachConcept(Map<Concept, List<LocalizedConceptData>> map, String preferredLang) {
        List<Concept> concepts = new ArrayList<>();
        for (Map.Entry<Concept, List<LocalizedConceptData>> entry : map.entrySet()) {
            List<LocalizedConceptData> datas = entry.getValue();
            int currentIndex = 0;
            while (currentIndex < datas.size() && !isPreferredLang(preferredLang, datas, currentIndex)) currentIndex++;
            if (currentIndex < datas.size()) {
                concepts.add(datas.get(currentIndex).getConcept());
            } else {
                concepts.add(datas.get(0).getConcept());
            }
        }
        return concepts;
    }

    private static boolean isPreferredLang(String preferredLang, List<LocalizedConceptData> datas, int currentIndex) {
        return datas.get(currentIndex).getLangCode().equals(preferredLang);
    }

    /**
     * Finds concepts matching the input label under the specified parent concept and language.
     * When the input is null or empty, it returns all concepts under the parent concept in the specified language.
     * Search is done by exact match and by similarity.
     * If no results are found, it falls back to searching without language restriction.
     * The results contain one concept per language, prioritizing the preferred language.
     * @param parentConcept The concept of the generic field
     * @param langCode   The language code
     * @param input      The input label to search for
     * @return List of unique concepts matching the input
     */
    @Transactional(readOnly = true)
    public List<Concept> findMatchingConcepts(Concept parentConcept, String langCode, String input, Pageable pageable) {
        if (input == null || input.isEmpty()) {
            return findAllConcepts(parentConcept, langCode);
        };

        Map<Concept, List<LocalizedConceptData>> result = new HashMap<>();

        fillData(result, localizedConceptDataRepository.findAllByLangCodeAndParentConceptAndLabelContaining(langCode, parentConcept.getId(), input, pageable));
        fillData(result, localizedConceptDataRepository.findConceptByFieldcodeAndLabelInputWithSimilarity(parentConcept.getId(), langCode, input, SIMILARITY_MIN_SCORE, pageable));

        if (result.isEmpty()) {
            fillData(result, localizedConceptDataRepository.findConceptByFieldcodeAndLabelInputWithSimilarityNoLang(parentConcept.getId(), input, SIMILARITY_MIN_SCORE));
        }

        return oneLangForEachConcept(result, langCode);
    }

    @Transactional(readOnly = true)
    public List<Concept> findAllCandidatesConcept(Concept parentConcept, String langCode, Pageable pageable) {
        return findMatchingConcepts(parentConcept , langCode, null, pageable);
    }

}
