package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConceptFromFieldCode;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldOptions;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.actionunit.DefaultProjectDefinition;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.CodeSourceService;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.database.repositories.measurement.UnitDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.VocabularyRepository;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Kition-Pervolia template: the types a Kition project keeps (stratigraphic unit, locus, sondage, tomb; the four kinds
 * of documentation), read from branches of the Kition thesaurus, the chronology and locus-nature lists read from other
 * branches of it, the positive/negative nature kept on the common-core thesaurus, and the text, date and measurement fields the source
 * sheets carry. See {@link KitionProjectDefinition}.
 *
 * <p>Like the default template it runs after the project's creation committed, and a step that cannot be done is logged and
 * skipped rather than failing the creation. The Kition thesaurus is read from its server while the template is applied
 * (each branch is imported by the first field that uses it).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KitionProjectTemplateApplier implements ProjectTemplateApplier {

    private static final int MIN_CODE = 1;
    private static final int MAX_CODE = 999;

    private final KitionProjectDefinition definition;
    private final DefaultProjectDefinition commonCore;
    private final ProjectThesaurusAssigner thesaurusAssigner;
    private final CodeSourceService codeSourceService;
    private final TableFieldConfigService tableFieldConfigService;
    private final FormConfigService formConfigService;
    private final VocabularyRepository vocabularyRepository;
    private final ConceptRepository conceptRepository;
    private final UnitDefinitionRepository unitDefinitionRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    public boolean supports(String templateId) {
        return ProjectTemplate.KITION_ID.equals(templateId);
    }

    @Override
    public boolean transactional() {
        return false;
    }

    @Override
    public void apply(UserInfo info, String templateId, Long newProjectId) {
        UserInfo previous = ExecutionContextHolder.get();
        ExecutionContextHolder.set(info);
        try {
            thesaurusAssigner.assignInstitutionOrCommonCore(newProjectId);
            applyAll(newProjectId);
        } catch (RuntimeException e) {
            log.warn("The Kition project {} could not be fully configured from {}", newProjectId, definition.thesaurusUrl(), e);
        } finally {
            if (previous != null) {
                ExecutionContextHolder.set(previous);
            } else {
                ExecutionContextHolder.clear();
            }
        }
    }

    private void applyAll(Long projectId) {
        for (KitionProjectDefinition.Table table : definition.tables()) {
            inNewTransaction(() -> useBranchAsTypes(projectId, table));
        }
        // each type needs its own form config before fields can be attached to it
        List<ConfiguredType> configured = new ArrayList<>();
        for (KitionProjectDefinition.Table table : definition.tables()) {
            for (KitionProjectDefinition.Type type : table.types()) {
                configureType(projectId, table.table(), type).ifPresent(configured::add);
            }
        }
        for (KitionProjectDefinition.BranchField branchField : definition.branchFields()) {
            boolean loaded = false;
            for (ConfiguredType type : typesOf(configured, branchField.table(), branchField.types())) {
                boolean alreadyLoaded = loaded;
                runSafely("branch field " + branchField.fieldCode() + " on " + type.label(),
                        () -> useBranchField(projectId, type, branchField, alreadyLoaded));
                loaded = true;
            }
        }
        for (KitionProjectDefinition.CommonCoreField field : definition.commonCoreFields()) {
            for (ConfiguredType type : typesOf(configured, field.table(), field.types())) {
                runSafely("common-core field " + field.fieldCode() + " on " + type.label(),
                        () -> useCommonCoreField(projectId, type, field));
            }
        }
        for (KitionProjectDefinition.ExtraField field : definition.extraFields()) {
            for (ConfiguredType type : typesOf(configured, field.table(), field.types())) {
                runSafely("field '" + field.name() + "' on " + type.label(), () -> addExtraField(projectId, type, field));
            }
        }
    }

    /** A type of the template once its form config exists in the project. */
    private record ConfiguredType(ConfigurableTable table, String label, Long conceptId, String name) {
    }

    private static List<ConfiguredType> typesOf(List<ConfiguredType> all, ConfigurableTable table, List<String> labels) {
        return all.stream()
                .filter(type -> type.table() == table && (labels.isEmpty() || labels.contains(type.label())))
                .toList();
    }

    private void useBranchAsTypes(Long projectId, KitionProjectDefinition.Table table) {
        try {
            codeSourceService.useBranch(projectId, table.table().getFieldCode(), branchTop(table.typeBranch()), null);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read the types of " + table.table() + " from " + table.typeBranch(), e);
        }
    }

    private Optional<ConfiguredType> configureType(Long projectId, ConfigurableTable table, KitionProjectDefinition.Type type) {
        Optional<Concept> concept = conceptRepository.findConceptByExternalIdIgnoreCase(definition.externalVocabularyId(), type.concept());
        if (concept.isEmpty()) {
            log.warn("Type '{}' ({}) of table {} is not in the local copy of {}: not configured",
                    type.label(), type.concept(), table, definition.thesaurusUrl());
            return Optional.empty();
        }
        Long conceptId = concept.get().getId();
        try {
            inNewTransaction(() -> {
                tableFieldConfigService.saveFormConfig(projectId, table, conceptId, TypeFormConfig.builder()
                        .identifierFormat(type.identifierFormat() != null ? type.identifierFormat() : table.getDefaultIdentifierFormat())
                        .minCode(MIN_CODE)
                        .maxCode(MAX_CODE)
                        .build());
                tableFieldConfigService.createOrGetFormConfig(projectId, table, conceptId);
            });
        } catch (RuntimeException e) {
            log.warn("Could not configure type '{}' of table {} for project {}", type.label(), table, projectId, e);
            return Optional.empty();
        }
        return tableFieldConfigService.typeNameOf(projectId, table, conceptId)
                .map(name -> new ConfiguredType(table, type.label(), conceptId, name));
    }

    private void useBranchField(Long projectId, ConfiguredType type, KitionProjectDefinition.BranchField spec, boolean branchLoaded) {
        FormConfig formConfig = formConfigOf(projectId, type);
        CustomFieldConcept field = vocabularyField(projectId, type, spec.fieldCode());
        if (branchLoaded) {
            formConfigService.addConceptConfigForLoadedBranch(formConfig, field, branchTop(spec.branch()));
        } else {
            formConfigService.addConceptConfigFor(formConfig, field, branchTop(spec.branch()));
        }
    }

    private void useCommonCoreField(Long projectId, ConfiguredType type, KitionProjectDefinition.CommonCoreField spec) {
        Vocabulary vocabulary = vocabularyRepository.findVocabularyByBaseUriAndVocabExternalId(
                        commonCore.baseUri(), commonCore.externalVocabularyId())
                .orElseThrow(() -> new IllegalStateException("Thesaurus " + commonCore.thesaurusUrl() + " is not imported"));
        formConfigService.addFieldCodeConfigFor(formConfigOf(projectId, type), vocabularyField(projectId, type, spec.fieldCode()),
                vocabulary, spec.fieldCode());
    }

    private void addExtraField(Long projectId, ConfiguredType type, KitionProjectDefinition.ExtraField spec) {
        tableFieldConfigService.createField(projectId, type.table(), type.name(), spec.name(), spec.fieldType(),
                spec.description(), optionsOf(spec));
    }

    /** A measurement is entered in the unit the definition names; the other kinds carry no option. */
    private FieldOptions optionsOf(KitionProjectDefinition.ExtraField spec) {
        if (spec.fieldType() != FieldType.MEASUREMENT) {
            return FieldOptions.NONE;
        }
        UnitDefinition unit = unitDefinitionRepository
                .findFirstBySymbolAndDimensionOrderByIdAsc(spec.unit(), UnitDefinition.Dimension.LENGTH)
                .orElseThrow(() -> new IllegalStateException("Unknown unit " + spec.unit()));
        return FieldOptions.units(unit.getId(), Set.of(unit.getId()));
    }

    private FormConfig formConfigOf(Long projectId, ConfiguredType type) {
        return tableFieldConfigService.createOrGetFormConfig(projectId, type.table(), type.conceptId())
                .orElseThrow(() -> new IllegalStateException("No form config for " + type.label()));
    }

    /** The system vocabulary field of the table carrying this field code, as configured on the type. */
    private CustomFieldConcept vocabularyField(Long projectId, ConfiguredType type, String fieldCode) {
        String label = SystemFieldCatalog.specsOf(type.table()).stream()
                .map(spec -> spec.field())
                .filter(CustomFieldConceptFromFieldCode.class::isInstance)
                .map(CustomFieldConceptFromFieldCode.class::cast)
                .filter(field -> fieldCode.equals(field.getFieldCode()))
                .map(CustomField::getLabel)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No vocabulary field " + fieldCode + " on " + type.table()));
        return tableFieldConfigService.findField(projectId, type.table(), type.conceptId(), label)
                .filter(CustomFieldConcept.class::isInstance)
                .map(CustomFieldConcept.class::cast)
                .orElseThrow(() -> new IllegalStateException("Field " + fieldCode + " is not on type " + type.label()));
    }

    private ConceptDTO branchTop(String externalConceptId) {
        return ConceptDTO.builder()
                .externalId(externalConceptId)
                .vocabulary(VocabularyDTO.builder()
                        .baseUri(definition.baseUri())
                        .externalVocabularyId(definition.externalVocabularyId())
                        .build())
                .deleted(false)
                .build();
    }

    private void runSafely(String what, Runnable work) {
        try {
            inNewTransaction(work);
        } catch (RuntimeException e) {
            log.warn("Kition template: {} was not configured", what, e);
        }
    }

    private void inNewTransaction(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> work.run());
    }
}
