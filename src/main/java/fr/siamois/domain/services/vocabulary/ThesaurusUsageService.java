package fr.siamois.domain.services.vocabulary;

import fr.siamois.domain.models.form.config.ConceptFieldFormConfig;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.FieldVocabularyConfig;
import fr.siamois.domain.models.vocabulary.ThesaurusUsage;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.domain.services.form.FormConfigService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.vocabulary.ConceptLabelDTO;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptAutocompleteDetachedDTO;
import fr.siamois.infrastructure.api.dto.concept.ConceptCollectionDetachedDTO;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptCollectionRepository;
import fr.siamois.mapper.vocabulary.VocabularyMapper;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    private final ConceptService conceptService;
    private final ConceptCollectionService conceptCollectionService;
    private final VocabularyMapper vocabularyMapper;
    private final ConceptCollectionRepository conceptCollectionRepository;
    private final PlatformTransactionManager transactionManager;

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
                .map(vocabulary -> describe(vocabulary, lang))
                .orElse("—");
        return Optional.of(new ThesaurusUsage(thesaurus, FieldVocabularyConfig.Kind.FIELD_CODE, code, field.getName(),
                typeLabel(table, typeName), table.name(), typeName, code, true));
    }

    /** A thesaurus as the pages name it: its label, its identifier on its instance, and the instance. */
    private String describe(Vocabulary vocabulary, String lang) {
        StringBuilder text = new StringBuilder(labelService.findLabelOf(vocabulary, lang).getValue());
        if (vocabulary.getExternalVocabularyId() != null) {
            text.append(" (").append(vocabulary.getExternalVocabularyId()).append(')');
        }
        String server = vocabulary.getBaseUri();
        if (server != null && !server.isBlank()) {
            String trimmed = server.trim();
            text.append(" - ").append(trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
        }
        return text.toString();
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

    private static FieldVocabularyConfig.Kind kindOf(ConceptFieldFormConfig config) {
        if (config.isBranchConfig()) {
            return FieldVocabularyConfig.Kind.BRANCH;
        }
        return config.isFieldCodeConfig() ? FieldVocabularyConfig.Kind.FIELD_CODE : FieldVocabularyConfig.Kind.COLLECTION;
    }

    private ThesaurusUsage toUsage(ConceptFieldFormConfig config, String lang, @Nullable ConfigurableTable table, @Nullable String typeName) {
        FieldVocabularyConfig.Kind kind = kindOf(config);
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
                describe(vocabulary, lang),
                kind,
                source,
                config.getField().getLabel(),
                table == null ? type : typeLabel(table, typeName),
                table == null ? null : table.name(),
                typeName,
                kind == FieldVocabularyConfig.Kind.FIELD_CODE ? config.getFieldCode() : null,
                false,
                vocabulary.getBaseUri() + "/?idt=" + vocabulary.getExternalVocabularyId(),
                config.getFormConfig().getId(),
                config.getField().getId());
    }

    /**
     * Moves the fields of the given rows to another thesaurus. A field on a field code keeps its code and looks it up
     * there; a field on a branch or a collection takes the one of the same name. A field whose code, branch or
     * collection the thesaurus does not have, or has more than once, is left as it is; a field given back to its
     * default thesaurus follows the default again.
     *
     * @param rows   the rows picked in the table
     * @param target the thesaurus the lists are now read in
     */
    public RewireResult rewireFieldCodes(@Nullable Long projectId, Long institutionId, List<ThesaurusUsage> rows, Vocabulary target) {
        int rewired = 0;
        int missing = 0;
        int ignored = 0;
        NamedSources named = new NamedSources(target);
        for (ThesaurusUsage row : rows) {
            switch (rewireRowSafely(projectId, institutionId, row, target, named)) {
                case REWIRED -> rewired++;
                case CODE_MISSING -> missing++;
                case IGNORED -> ignored++;
            }
        }
        return new RewireResult(rewired, missing, ignored);
    }

    private enum Outcome { REWIRED, CODE_MISSING, IGNORED }

    /**
     * Each field is moved in a transaction of its own, so the thesaurus is never read while one is held open for several
     * fields, and a field that cannot be moved is left as it is without undoing the others.
     */
    private void inNewTransaction(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> work.run());
    }

    /** What the target thesaurus holds under a name, read once however many fields ask for it. */
    private final class NamedSources {
        private final VocabularyDTO target;
        private final Map<String, Optional<ConceptDTO>> branches = new HashMap<>();
        private final Map<String, Optional<ConceptCollectionDetachedDTO>> collections = new HashMap<>();
        private List<ConceptCollectionDetachedDTO> allCollections;
        /** The branches and collections already imported for a field, so the next field on them does not import them again. */
        private final Set<String> importedBranches = new HashSet<>();
        private final Set<String> importedCollections = new HashSet<>();

        NamedSources(Vocabulary target) {
            this.target = vocabularyMapper.convert(target);
        }

        /** The one branch top term of the thesaurus named {@code name}, empty when there is none or several. */
        Optional<ConceptDTO> branch(String name) {
            return branches.computeIfAbsent(normalized(name), key -> findBranch(name));
        }

        /** The one collection of the thesaurus named {@code name}, empty when there is none or several. */
        Optional<ConceptCollectionDetachedDTO> collection(String name) {
            return collections.computeIfAbsent(normalized(name), this::findCollection);
        }

        private Optional<ConceptDTO> findBranch(String name) {
            try {
                List<ConceptDTO> found = conceptService.fetchAutocompleteFromRemoteThesaurus(target, name).stream()
                        .filter(result -> !result.getConceptLabelToDisplay().isAltLabel())
                        .filter(result -> normalized(result.getConceptLabelToDisplay().getLabel()).equals(normalized(name)))
                        .map(ConceptAutocompleteDetachedDTO::getConceptLabelToDisplay)
                        .map(ConceptLabelDTO::getConcept)
                        .collect(Collectors.toMap(ConceptDTO::getExternalId, Function.identity(), (first, second) -> first))
                        .values().stream().toList();
                return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
            } catch (JsonProcessingException | RuntimeException e) {
                log.warn("Could not look for the branch '{}' in {}", name, target.getBaseUri(), e);
                return Optional.empty();
            }
        }

        private Optional<ConceptCollectionDetachedDTO> findCollection(String key) {
            try {
                if (allCollections == null) {
                    allCollections = conceptCollectionService.fetchCollectionsFromRemoteThesaurus(target);
                }
                List<ConceptCollectionDetachedDTO> found = allCollections.stream()
                        .filter(collection -> normalized(collection.getLabelToDisplay()).equals(key)
                                || collection.getLabels().stream().anyMatch(label -> normalized(label.getTitle()).equals(key)))
                        .toList();
                return found.size() == 1 ? Optional.of(found.get(0)) : Optional.empty();
            } catch (RuntimeException e) {
                log.warn("Could not look for the collection '{}' in {}", key, target.getBaseUri(), e);
                return Optional.empty();
            }
        }
    }

    private static String normalized(@Nullable String name) {
        return name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private Outcome rewireRowSafely(@Nullable Long projectId, Long institutionId, ThesaurusUsage row, Vocabulary target, NamedSources named) {
        try {
            return rewireRow(projectId, institutionId, row, target, named);
        } catch (RuntimeException e) {
            log.warn("Field '{}' of {} / {} could not be moved to another thesaurus", row.getFieldLabel(), row.getTableName(), row.getTypeName(), e);
            return Outcome.IGNORED;
        }
    }

    /** The form configuration and the field a row is about. */
    private record RewireTarget(FormConfig formConfig, CustomFieldConcept field) {
    }

    /**
     * Finds what a row is about: through the ids it carries when something is stored for it, else through the table, type and
     * label of an inherited row of a project.
     */
    private Optional<RewireTarget> resolveTarget(@Nullable Long projectId, ThesaurusUsage row) {
        if (row.getFormConfigId() != null && row.getFieldId() != null) {
            return fieldFormConfigRepository.findAllByFormConfigId(row.getFormConfigId()).stream()
                    .filter(config -> config.getField() != null && row.getFieldId().equals(config.getField().getId()))
                    .findFirst()
                    .filter(config -> Hibernate.unproxy(config.getField()) instanceof CustomFieldConcept)
                    .map(config -> new RewireTarget(config.getFormConfig(), (CustomFieldConcept) Hibernate.unproxy(config.getField())));
        }
        if (projectId == null || row.getTableName() == null) {
            return Optional.empty();
        }
        ConfigurableTable table = ConfigurableTable.valueOf(row.getTableName());
        Optional<FormConfig> formConfig = tableFieldConfigService.findFormConfig(projectId, table, row.getTypeName());
        Optional<CustomField> field = tableFieldConfigService.findField(projectId, table, row.getTypeName(), row.getFieldLabel());
        if (formConfig.isEmpty() || field.isEmpty() || !(field.get() instanceof CustomFieldConcept conceptField)) {
            log.warn("Field '{}' of {} / {} was not found to be connected to another thesaurus", row.getFieldLabel(), row.getTableName(), row.getTypeName());
            return Optional.empty();
        }
        return Optional.of(new RewireTarget(formConfig.get(), conceptField));
    }

    private Outcome rewireRow(@Nullable Long projectId, Long institutionId, ThesaurusUsage row, Vocabulary target, NamedSources named) {
        boolean isCode = row.getKind() == FieldVocabularyConfig.Kind.FIELD_CODE;
        String code = row.getFieldCode();
        if (isCode && code == null) {
            return Outcome.IGNORED;
        }
        if (isCode && fieldCodeResolver.findIn(target, code).isEmpty()) {
            return Outcome.CODE_MISSING;
        }
        Optional<RewireTarget> found = resolveTarget(projectId, row);
        if (found.isEmpty()) {
            return Outcome.IGNORED;
        }
        if (!isCode) {
            return rewireByName(found.get(), target, named, row.getKind() == FieldVocabularyConfig.Kind.BRANCH);
        }
        inNewTransaction(() -> rewire(found.get(), projectId, institutionId, code, target));
        return Outcome.REWIRED;
    }

    /** A branch or a collection is found again in the target thesaurus by the name it was picked under. */
    private Outcome rewireByName(RewireTarget at, Vocabulary target, NamedSources named, boolean branch) {
        String name = formConfigService.findConceptConfigFor(at.formConfig(), at.field())
                .map(config -> branch ? config.getBranchName() : config.getCollectionName())
                .orElse(null);
        if (name == null || name.isBlank()) {
            return Outcome.IGNORED;
        }
        // looked up before any transaction opens: the thesaurus is slow
        return branch
                ? rewireBranch(at.formConfig(), at.field(), name, named)
                : rewireCollection(at.formConfig(), at.field(), name, target, named);
    }

    private Outcome rewireBranch(FormConfig formConfig, CustomFieldConcept conceptField, String name, NamedSources named) {
        Optional<ConceptDTO> found = named.branch(name);
        if (found.isEmpty()) {
            return Outcome.IGNORED;
        }
        ConceptDTO top = found.get();
        boolean imported = named.importedBranches.contains(top.getExternalId());
        inNewTransaction(() -> {
            if (imported) {
                formConfigService.addConceptConfigForLoadedBranch(formConfig, conceptField, top);
            } else {
                formConfigService.addConceptConfigFor(formConfig, conceptField, top);
            }
            formConfigService.rememberSourceName(formConfig, conceptField, name);
        });
        named.importedBranches.add(top.getExternalId());
        return Outcome.REWIRED;
    }

    private Outcome rewireCollection(FormConfig formConfig, CustomFieldConcept conceptField, String name,
                                     Vocabulary target, NamedSources named) {
        Optional<ConceptCollectionDetachedDTO> found = named.collection(name);
        if (found.isEmpty()) {
            return Outcome.IGNORED;
        }
        ConceptCollectionDetachedDTO collection = found.get();
        Optional<ConceptCollection> local = named.importedCollections.contains(collection.getExternalId())
                ? conceptCollectionRepository.findByVocabularyAndExternalId(target, collection.getExternalId())
                : Optional.empty();
        inNewTransaction(() -> {
            if (local.isPresent()) {
                formConfigService.addConceptConfigFor(formConfig, conceptField, local.get());
            } else {
                formConfigService.addConceptConfigFor(formConfig, conceptField, collection);
            }
            formConfigService.rememberSourceName(formConfig, conceptField, name);
        });
        named.importedCollections.add(collection.getExternalId());
        return Outcome.REWIRED;
    }

    private void rewire(RewireTarget at, @Nullable Long projectId, Long institutionId, String code, Vocabulary target) {
        boolean isDefault = fieldCodeResolver.findDefaultVocabulary(institutionId, projectId, code)
                .map(found -> found.getId() != null && found.getId().equals(target.getId()))
                .orElse(false);
        if (isDefault) {
            formConfigService.clearConceptConfigFor(at.formConfig(), at.field());
        } else {
            formConfigService.addFieldCodeConfigFor(at.formConfig(), at.field(), target, code);
        }
    }
}
