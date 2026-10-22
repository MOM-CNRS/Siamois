package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Tells which thesaurus branches and collections a project or an organisation uses, and where.
 */
@Service
@RequiredArgsConstructor
public class ThesaurusUsageService {

    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final LabelService labelService;

    @Transactional(readOnly = true)
    public List<ThesaurusUsage> findUsagesOfActionUnit(Long actionUnitId, String lang) {
        return toUsages(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(actionUnitId), lang);
    }

    @Transactional(readOnly = true)
    public List<ThesaurusUsage> findUsagesOfInstitution(Long institutionId, String lang) {
        return toUsages(fieldFormConfigRepository.findVocabularyRestrictionsOfInstitution(institutionId), lang);
    }

    private List<ThesaurusUsage> toUsages(List<ConceptFieldFormConfig> configs, String lang) {
        return configs.stream()
                .map(config -> toUsage(config, lang))
                .sorted(Comparator.comparing(ThesaurusUsage::getThesaurusLabel)
                        .thenComparing(ThesaurusUsage::getFieldLabel))
                .toList();
    }

    private ThesaurusUsage toUsage(ConceptFieldFormConfig config, String lang) {
        boolean branch = config.isBranchConfig();
        Vocabulary vocabulary = branch
                ? config.getBranchTopTerm().getVocabulary()
                : config.getCollection().getVocabulary();
        String source = branch
                ? labelService.findLabelOf(config.getBranchTopTerm(), lang).getLabel()
                : config.getCollection().getExternalId();
        var valueConcept = config.getFormConfig().getValueConcept();
        return new ThesaurusUsage(
                labelService.findLabelOf(vocabulary, lang).getValue(),
                branch ? FieldVocabularyConfig.Kind.BRANCH : FieldVocabularyConfig.Kind.COLLECTION,
                source,
                config.getField().getLabel(),
                valueConcept == null ? null : labelService.findLabelOf(valueConcept, lang).getLabel(),
                config.isFrozen());
    }
}
