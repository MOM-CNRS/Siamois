package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tells which thesaurus field codes, branches and collections a project or an organisation uses, and where.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThesaurusUsageService {

    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final LabelService labelService;
    private final TableFieldConfigService tableFieldConfigService;
    private final FormConfigService formConfigService;
    private final FieldCodeResolver fieldCodeResolver;

    /** What {@link #rewireFieldCodes} did with the rows it was given. */
    public record RewireResult(int rewired, int codeMissing, int ignored) {
    }

    /** The sources stored for a project, leaving out the fields that follow the default source of their code. */
    @Transactional(readOnly = true)
    public List<ThesaurusUsage> findUsagesOfActionUnit(Long actionUnitId, String lang) {
        return sorted(fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(actionUnitId).stream()
                .map(config -> toUsage(config, lang, null, null))
                .toList());
    }

    /**
     * Every vocabulary field of the types a project configures, with the source it draws its list from: the
     * stored one, or the default source of its field code when nothing is stored for it.
     */
    @Transactional(readOnly = true)
    public List<ThesaurusUsage> findAllUsagesOfProject(Long projectId, @Nullable Long institutionId, String lang) {
        List<ThesaurusUsage> usages = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ConfigurableTable table : tableFieldConfigService.listTables()) {
            for (var type : tableFieldConfigService.listTypes(projectId, table)) {
                collectTypeUsages(projectId, institutionId, lang, table, type.getName(), usages, seen);
            }
        }
        for (ConceptFieldFormConfig config : fieldFormConfigRepository.findVocabularyRestrictionsOfActionUnit(projectId)) {
            if (seen.add(keyOf(config.getFormConfig(), config.getField()))) {
                usages.add(toUsage(config, lang, null, null));
            }
        }
        return sorted(usages);
    }

    @Transactional(readOnly = true)
    public List<ThesaurusUsage> findUsagesOfInstitution(Long institutionId, String lang) {
        return sorted(fieldFormConfigRepository.findVocabularyRestrictionsOfInstitution(institutionId).stream()
                .map(config -> toUsage(config, lang, null, null))
                .toList());
    }

    private void collectTypeUsages(Long projectId, @Nullable Long institutionId, String lang, ConfigurableTable table,
                                   String typeName, List<ThesaurusUsage> into, Set<String> seen) {
        Optional<FormConfig> formConfig = tableFieldConfigService.findFormConfig(projectId, table, typeName);
        if (formConfig.isEmpty()) {
            return;
        }
        Map<Long, ConceptFieldFormConfig> stored = fieldFormConfigRepository.findAllByFormConfigId(formConfig.get().getId()).stream()
                .filter(ConceptFieldFormConfig.class::isInstance)
                .map(ConceptFieldFormConfig.class::cast)
                .collect(Collectors.toMap(config -> config.getField().getId(), Function.identity(), (first, second) -> first));
        for (TypeFieldFormConfig field : tableFieldConfigService.getFieldsConfig(projectId, table, typeName).getFields()) {
            if (!field.getType().isConfigurable() || field.getId() == null) {
                continue;
            }
            ConceptFieldFormConfig config = stored.get(field.getId());
            String key = formConfig.get().getId() + ":" + field.getId();
            if (config != null && !config.isNotValid()) {
                seen.add(key);
                into.add(toUsage(config, lang, table, typeName));
            } else if (isCode(field.getSourceLabel()) && seen.add(key)) {
                inheritedUsage(field, table, typeName, projectId, institutionId, lang).ifPresent(into::add);
            }
        }
    }

    private Optional<ThesaurusUsage> inheritedUsage(TypeFieldFormConfig field, ConfigurableTable table, String typeName,
                                                    Long projectId, @Nullable Long institutionId, String lang) {
        String code = field.getSourceLabel();
        String thesaurus = institutionId == null ? "—" : fieldCodeResolver.findDefaultVocabulary(institutionId, projectId, code)
                .map(vocabulary -> labelService.findLabelOf(vocabulary, lang).getValue())
                .orElse("—");
        return Optional.of(new ThesaurusUsage(thesaurus, FieldVocabularyConfig.Kind.FIELD_CODE, code, field.getName(),
                typeLabel(table, typeName), table.name(), typeName, code, true));
    }

    private static boolean isCode(@Nullable String sourceLabel) {
        return sourceLabel != null && !sourceLabel.isBlank() && !"—".equals(sourceLabel);
    }

    private static String keyOf(FormConfig formConfig, CustomField field) {
        return formConfig.getId() + ":" + field.getId();
    }

    private static String typeLabel(@Nullable ConfigurableTable table, @Nullable String typeName) {
        if (table == null) {
            return typeName;
        }
        return typeName == null ? table.getLabel() : table.getLabel() + " · " + typeName;
    }

    private List<ThesaurusUsage> sorted(List<ThesaurusUsage> usages) {
        return usages.stream()
                .sorted(Comparator.comparing(ThesaurusUsage::getThesaurusLabel)
                        .thenComparing(ThesaurusUsage::getFieldLabel)
                        .thenComparing(usage -> usage.getTypeLabel() == null ? "" : usage.getTypeLabel()))
                .toList();
    }

    private ThesaurusUsage toUsage(ConceptFieldFormConfig config, String lang, @Nullable ConfigurableTable table, @Nullable String typeName) {
        FieldVocabularyConfig.Kind kind = config.isBranchConfig() ? FieldVocabularyConfig.Kind.BRANCH
                : config.isFieldCodeConfig() ? FieldVocabularyConfig.Kind.FIELD_CODE
                : FieldVocabularyConfig.Kind.COLLECTION;
        Vocabulary vocabulary = switch (kind) {
            case BRANCH -> config.getBranchTopTerm().getVocabulary();
            case FIELD_CODE -> config.getVocabulary();
            case COLLECTION -> config.getCollection().getVocabulary();
        };
        String source = switch (kind) {
            case BRANCH -> labelService.findLabelOf(config.getBranchTopTerm(), lang).getLabel();
            case FIELD_CODE -> config.getFieldCode();
            case COLLECTION -> config.getCollection().getExternalId();
        };
        String type = typeName;
        if (table == null) {
            var valueConcept = config.getFormConfig().getValueConcept();
            type = valueConcept == null ? null : labelService.findLabelOf(valueConcept, lang).getLabel();
        }
        return new ThesaurusUsage(
                labelService.findLabelOf(vocabulary, lang).getValue(),
                kind,
                source,
                config.getField().getLabel(),
                table == null ? type : typeLabel(table, typeName),
                table == null ? null : table.name(),
                typeName,
                kind == FieldVocabularyConfig.Kind.FIELD_CODE ? config.getFieldCode() : null,
                false);
    }

    /**
     * Connects the fields of the given rows that use a field code to the same code in another thesaurus. A field whose code
     * the thesaurus does not define is left as it is; a field given back to its default thesaurus follows the default again.
     *
     * @param rows   the rows picked in the table; those not using a field code are ignored
     * @param target the thesaurus the codes are now read in
     */
    @Transactional(rollbackFor = Exception.class)
    public RewireResult rewireFieldCodes(Long projectId, Long institutionId, List<ThesaurusUsage> rows, Vocabulary target) {
        int rewired = 0;
        int missing = 0;
        int ignored = 0;
        for (ThesaurusUsage row : rows) {
            if (row.getKind() != FieldVocabularyConfig.Kind.FIELD_CODE || row.getTableName() == null || row.getFieldCode() == null) {
                ignored++;
                continue;
            }
            if (fieldCodeResolver.findIn(target, row.getFieldCode()).isEmpty()) {
                missing++;
                continue;
            }
            if (rewire(projectId, institutionId, row, target)) {
                rewired++;
            } else {
                ignored++;
            }
        }
        return new RewireResult(rewired, missing, ignored);
    }

    private boolean rewire(Long projectId, Long institutionId, ThesaurusUsage row, Vocabulary target) {
        ConfigurableTable table = ConfigurableTable.valueOf(row.getTableName());
        Optional<FormConfig> formConfig = tableFieldConfigService.findFormConfig(projectId, table, row.getTypeName());
        Optional<CustomField> field = tableFieldConfigService.findField(projectId, table, row.getTypeName(), row.getFieldLabel());
        if (formConfig.isEmpty() || field.isEmpty() || !(field.get() instanceof CustomFieldConcept conceptField)) {
            log.warn("Field '{}' of {} / {} was not found to be connected to another thesaurus", row.getFieldLabel(), row.getTableName(), row.getTypeName());
            return false;
        }
        boolean isDefault = fieldCodeResolver.findDefaultVocabulary(institutionId, projectId, row.getFieldCode())
                .map(found -> found.getId() != null && found.getId().equals(target.getId()))
                .orElse(false);
        if (isDefault) {
            formConfigService.clearConceptConfigFor(formConfig.get(), conceptField);
        } else {
            formConfigService.addFieldCodeConfigFor(formConfig.get(), conceptField, target, row.getFieldCode());
        }
        return true;
    }
}
