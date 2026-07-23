package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;

/**
 * Reads and writes the per-type field configuration of a table on top of {@code FormConfig} /
 * {@code FieldFormConfig}.
 * <p>
 * A table maps to the "type" field of its entity ({@link ConfigurableTable#getFieldCode()}); the
 * concept that field is configured on is the {@code fieldConcept} of every {@code FormConfig} of
 * that table. A type name is the label of a value of that field, which is the {@code valueConcept}
 * of the configuration; {@code _default} is the configuration carrying no value concept, the one
 * that applies whenever a value has none of its own.
 * <p>
 * Configurations are created lazily: a type has no row until something is actually configured on
 * it. Reading a type that has no configuration therefore falls back on the default one rather than
 * writing to the database, and only the write operations materialize a row.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class TableFieldConfigServiceImpl implements TableFieldConfigService {

    /**
     * Name the UI addresses the configuration carrying no {@code valueConcept} by. Mirrors the
     * constant of {@link MockTableFieldConfigService}; both implementations answer to it.
     */
    private static final String DEFAULT_TYPE = "_default";

    private static final String NEW_FIELD_BASE_NAME = "Nouveau champ";
    private static final String NO_SOURCE = "—";

    private final FieldConfigurationService fieldConfigurationService;
    private final LabelService labelService;
    private final ActionUnitRepository actionUnitRepository;
    private final ConceptRepository conceptRepository;
    private final FormRepository formRepository;
    private final FormConfigRepository formConfigRepository;
    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final CustomFieldRepository customFieldRepository;
    private final PersonRepository personRepository;

    @Override
    public List<ConfigurableTable> listTables() {
        return List.of(ConfigurableTable.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TypeSummary> listTypes(Long projectId, ConfigurableTable table) {
        List<TypeSummary> types = new ArrayList<>();
        types.add(new TypeSummary(DEFAULT_TYPE, true));
        configuredTypeNames(projectId, table).stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> new TypeSummary(name, false))
                .forEach(types::add);
        return types;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listConfigurableTypes(Long projectId, ConfigurableTable table, String input) {
        Set<String> configured = configuredTypeNames(projectId, table);
        Set<String> candidates = new LinkedHashSet<>();
        for (ConceptAutocompleteDTO value : fieldValues(projectId, table, input)) {
            candidates.add(value.getConceptLabelToDisplay().getLabel());
        }
        candidates.removeAll(configured);
        return List.copyOf(candidates);
    }

    @Override
    @Transactional
    public TypeSummary addConfiguration(Long projectId, ConfigurableTable table, String typeName) {
        if (DEFAULT_TYPE.equals(typeName)) {
            throw new IllegalArgumentException("The default configuration always exists and cannot be added");
        }
        requireFormConfig(projectId, table, typeName);
        return new TypeSummary(typeName, false);
    }

    /**
     * The names of the types this project configured for a table, i.e. the value concepts its form
     * configurations carry. The default configuration has no value concept and is not one of them.
     */
    private Set<String> configuredTypeNames(Long projectId, ConfigurableTable table) {
        Optional<Concept> fieldConcept = findFieldConcept(projectId, table);
        if (fieldConcept.isEmpty()) return Set.of();

        Set<String> names = new LinkedHashSet<>();
        for (FormConfig config : formConfigRepository.findAllByActionUnitAndField(projectId, fieldConcept.get().getId())) {
            if (config.getValueConcept() != null) {
                names.add(labelOf(config.getValueConcept()));
            }
        }
        return names;
    }

    @Override
    @Transactional(readOnly = true)
    public TypeFormConfig getFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        boolean isDefault = DEFAULT_TYPE.equals(typeName);
        String valueConceptLabel = findFormConfig(projectId, table, typeName)
                .map(FormConfig::getValueConcept)
                .map(this::labelOf)
                .orElse(isDefault ? "" : typeName);
        return TypeFormConfig.builder()
                .typeName(typeName)
                .valueConceptLabel(isDefault ? "" : valueConceptLabel)
                .description("")
                .inheritsDefaultFields(!isDefault)
                .visibleInApp(true)
                .build();
    }

    /**
     * Materializes the type's configuration so later field edits have somewhere to be written.
     * <p>
     * {@code description}, {@code inheritsDefaultFields} and {@code visibleInApp} are dropped: the
     * {@code FormConfig} entity has no column for them, so there is nothing to save them into.
     */
    @Override
    @Transactional
    public void saveFormConfig(Long projectId, ConfigurableTable table, TypeFormConfig config) {
        requireFormConfig(projectId, table, config.getTypeName());
        log.debug("Form config of type '{}' on table {} materialized for project {}; description, " +
                        "inheritance and visibility are not persisted (no column on FormConfig)",
                config.getTypeName(), table, projectId);
    }

    /**
     * Fields of a type: the fields of the form that applies to it — that is where the system fields
     * come from, since nothing configures them until somebody changes one — carrying the stored
     * configuration of the ones that have been configured, plus the additional fields this screen
     * added on top of the form.
     */
    @Override
    @Transactional(readOnly = true)
    public TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, String typeName) {
        TypeFieldsConfig config = new TypeFieldsConfig();
        config.setFields(new ArrayList<>(effectiveFields(projectId, table, typeName).values().stream()
                .map(this::toDto)
                .toList()));
        return config;
    }

    @Override
    @Transactional
    public void setFieldActive(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean active) {
        updateField(projectId, table, typeName, fieldName, config -> config.setActive(active));
    }

    @Override
    @Transactional
    public void setFieldMandatory(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean mandatory) {
        updateField(projectId, table, typeName, fieldName, config -> config.setMandatory(mandatory));
    }

    @Override
    @Transactional
    public TypeFieldFormConfig addAdditionalField(Long projectId, ConfigurableTable table, String typeName) {
        FormConfig formConfig = requireFormConfig(projectId, table, typeName);
        CustomFieldText field = CustomFieldText.builder()
                .label(nextNewFieldName(projectId, table, typeName))
                .isSystemField(false)
                .isTextArea(false)
                .author(currentPerson())
                .build();
        CustomField saved = customFieldRepository.save(field);

        FieldFormConfig link = new FieldFormConfig();
        link.setField(saved);
        link.setFormConfig(formConfig);
        link.setActive(true);
        link.setMandatory(false);
        link.setInstitutionLocked(false);
        return toDto(fieldFormConfigRepository.save(link));
    }

    /**
     * Unlinks an additional field from the type. The custom field itself is deleted only once no
     * other configuration references it, since the same field can be configured on several types.
     */
    @Override
    @Transactional
    public void deleteAdditionalField(Long projectId, ConfigurableTable table, String typeName, String fieldName) {
        Optional<FormConfig> formConfig = findFormConfig(projectId, table, typeName);
        if (formConfig.isEmpty()) return;

        fieldFormConfigRepository.findAllByFormConfigId(formConfig.get().getId()).stream()
                .filter(config -> !isSystemField(config.getField()))
                .filter(config -> fieldName.equals(config.getField().getLabel()))
                .findFirst()
                .ifPresent(config -> {
                    CustomField field = config.getField();
                    fieldFormConfigRepository.delete(config);
                    if (fieldFormConfigRepository.countByFieldId(field.getId()) == 0) {
                        customFieldRepository.delete(field);
                    }
                });
    }

    /**
     * A field as it applies to a type: the custom field itself, the configuration stored for it if
     * there is one — the type's own, or the one it inherits from the default configuration — and the
     * requiredness the form declares, which is what {@code mandatory} falls back on while the field
     * has no stored configuration.
     */
    private record EffectiveField(CustomField field, @Nullable FieldFormConfig stored, boolean requiredByForm) {

        boolean active() {
            return stored == null || stored.isActive();
        }

        boolean mandatory() {
            return stored == null ? requiredByForm : stored.isMandatory();
        }

        boolean institutionLocked() {
            return stored != null && stored.isInstitutionLocked();
        }
    }

    /**
     * The fields that apply to a type, keyed by custom field id and in the order the form lays them
     * out. The form is the source of the list: a system field exists for the screen as soon as the
     * form carries it, whether or not anything was ever configured on it. Fields configured but
     * absent from the form — the ones added from this screen — come last.
     */
    private Map<Long, EffectiveField> effectiveFields(Long projectId, ConfigurableTable table, String typeName) {
        Map<Long, FieldFormConfig> stored = storedFields(projectId, table, typeName);
        Map<Long, EffectiveField> fields = new LinkedHashMap<>();

        for (FormField formField : formFields(projectId, table, typeName)) {
            CustomField field = formField.field();
            fields.put(field.getId(), new EffectiveField(field, stored.get(field.getId()), formField.required()));
        }
        stored.forEach((fieldId, config) ->
                fields.computeIfAbsent(fieldId, id -> new EffectiveField(config.getField(), config, false)));
        return fields;
    }

    /**
     * The configurations stored for a type, keyed by custom field id: the default configuration's
     * ones first, then the type's own ones, which replace the inherited entry for the same field.
     */
    private Map<Long, FieldFormConfig> storedFields(Long projectId, ConfigurableTable table, String typeName) {
        Map<Long, FieldFormConfig> fields = new LinkedHashMap<>();
        if (!DEFAULT_TYPE.equals(typeName)) {
            collectFields(findFormConfig(projectId, table, DEFAULT_TYPE), fields);
        }
        collectFields(findFormConfig(projectId, table, typeName), fields);
        return fields;
    }

    private void collectFields(Optional<FormConfig> formConfig, Map<Long, FieldFormConfig> into) {
        formConfig.ifPresent(config -> fieldFormConfigRepository.findAllByFormConfigId(config.getId())
                .forEach(field -> into.put(field.getField().getId(), field)));
    }

    /** A field the form lays out, with the requiredness the form declares for it. */
    private record FormField(CustomField field, boolean required) {
    }

    /**
     * The fields of the form that applies to a type, in layout order. The form is the one the
     * application would display for that type — resolved by the same institution/type cascade the
     * data entry screens use — so the configuration screen lists exactly the fields the user fills
     * in.
     */
    private List<FormField> formFields(Long projectId, ConfigurableTable table, String typeName) {
        Long valueConceptId = DEFAULT_TYPE.equals(typeName) ? null
                : findFieldConcept(projectId, table)
                .flatMap(fieldConcept -> findValueConcept(projectId, fieldConcept, typeName))
                .map(Concept::getId)
                .orElse(null);

        Optional<Long> formId = formRepository.findEffectiveFormIdByTypeAndInstitution(
                valueConceptId, currentUser().getInstitution().getId());
        if (formId.isEmpty()) {
            log.warn("No form applies to type '{}' of table {}; the screen has no system field to show",
                    typeName, table);
            return List.of();
        }
        return layoutFieldsOf(formId.get());
    }

    /**
     * Loads the fields a form lays out from the layout's raw ids rather than from the
     * {@link fr.siamois.domain.models.form.customform.CustomForm} entity, whose layout converter
     * hands out <em>detached</em> fields — attaching one of those to a configuration, or merely
     * leaving it behind in a read-write transaction, breaks the next flush.
     */
    private List<FormField> layoutFieldsOf(Long formId) {
        Map<Long, Boolean> requiredByFieldId = new LinkedHashMap<>();
        for (Object[] layoutField : formRepository.findLayoutFieldsByFormId(formId)) {
            requiredByFieldId.putIfAbsent(((Number) layoutField[0]).longValue(), Boolean.TRUE.equals(layoutField[1]));
        }

        Map<Long, CustomField> fields = new HashMap<>();
        customFieldRepository.findAllById(requiredByFieldId.keySet()).forEach(field -> fields.put(field.getId(), field));

        List<FormField> formFields = new ArrayList<>();
        requiredByFieldId.forEach((fieldId, required) -> {
            CustomField field = fields.get(fieldId);
            if (field != null) {
                formFields.add(new FormField(field, required));
            }
        });
        return formFields;
    }

    private Optional<FormConfig> findFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        Optional<Concept> fieldConcept = findFieldConcept(projectId, table);
        if (fieldConcept.isEmpty()) return Optional.empty();

        if (DEFAULT_TYPE.equals(typeName)) {
            return formConfigRepository.findDefaultByActionUnitAndField(projectId, fieldConcept.get().getId());
        }
        return findValueConcept(projectId, fieldConcept.get(), typeName)
                .flatMap(value -> formConfigRepository.findByActionUnitAndFieldAndValue(
                        projectId, fieldConcept.get().getId(), value.getId()));
    }

    /**
     * Applies a change to the type's configuration of a field. The row is created when the field is
     * still configured nowhere — a system field the form declares but nobody ever touched — and when
     * it is only inherited from the default configuration, which must not be edited in place or the
     * change would leak to every other type.
     * <p>
     * A field the institution locked is left untouched: its state can only be changed from the
     * (not yet implemented) institution-level screens.
     */
    private void updateField(Long projectId, ConfigurableTable table, String typeName, String fieldName,
                             Consumer<FieldFormConfig> change) {
        Optional<EffectiveField> target = effectiveFields(projectId, table, typeName).values().stream()
                .filter(field -> fieldName.equals(field.field().getLabel()))
                .findFirst();

        if (target.isEmpty()) {
            log.warn("No field '{}' on type '{}' of table {} in project {}",
                    fieldName, typeName, table, projectId);
            return;
        }
        EffectiveField field = target.get();
        if (field.institutionLocked()) {
            log.debug("Field '{}' is locked by the institution, project {} cannot change it", fieldName, projectId);
            return;
        }

        FormConfig owner = requireFormConfig(projectId, table, typeName);
        FieldFormConfig config = ownedBy(field, owner) ? field.stored() : materialize(field, owner);
        change.accept(config);
        fieldFormConfigRepository.save(config);
    }

    private boolean ownedBy(EffectiveField field, FormConfig owner) {
        return field.stored() != null && owner.getId().equals(field.stored().getFormConfig().getId());
    }

    /**
     * Gives the type its own configuration row for a field, starting from the state the field
     * currently shows — the values it inherits from the default configuration, or the ones the form
     * declares when nothing was ever stored for it.
     */
    private FieldFormConfig materialize(EffectiveField field, FormConfig owner) {
        FieldFormConfig config = new FieldFormConfig();
        config.setField(field.field());
        config.setFormConfig(owner);
        config.setActive(field.active());
        config.setMandatory(field.mandatory());
        config.setInstitutionLocked(field.institutionLocked());
        return config;
    }

    private FormConfig requireFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        return findFormConfig(projectId, table, typeName)
                .orElseGet(() -> createFormConfig(projectId, table, typeName));
    }

    private FormConfig createFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        ActionUnit project = actionUnitRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Unknown project: " + projectId));
        Concept fieldConcept = findFieldConcept(projectId, table)
                .orElseThrow(() -> new IllegalStateException(
                        "No vocabulary configured for field " + table.getFieldCode() + " of project " + projectId));

        FormConfig config = new FormConfig();
        config.setActionUnit(project);
        config.setInstitution(project.getCreatedByInstitution());
        config.setFieldConcept(fieldConcept);
        config.setFieldConfigs(new HashSet<>());
        if (!DEFAULT_TYPE.equals(typeName)) {
            config.setValueConcept(findValueConcept(projectId, fieldConcept, typeName)
                    .orElseThrow(() -> new NoSuchElementException(
                            "Unknown type '" + typeName + "' for table " + table)));
        }
        return formConfigRepository.save(config);
    }

    private String nextNewFieldName(Long projectId, ConfigurableTable table, String typeName) {
        List<String> existing = effectiveFields(projectId, table, typeName).values().stream()
                .map(field -> field.field().getLabel())
                .toList();
        if (!existing.contains(NEW_FIELD_BASE_NAME)) return NEW_FIELD_BASE_NAME;
        int suffix = 2;
        while (existing.contains(NEW_FIELD_BASE_NAME + " " + suffix)) suffix++;
        return NEW_FIELD_BASE_NAME + " " + suffix;
    }

    /**
     * The concept the table's type field is configured on, i.e. the root of the vocabulary its
     * values are taken from. Empty when the project has no configuration for that field, which is
     * a setup problem rather than an error of this screen — the tables are then simply typeless.
     */
    private Optional<Concept> findFieldConcept(Long projectId, ConfigurableTable table) {
        try {
            return Optional.of(fieldConfigurationService
                    .findConfigurationForFieldCode(currentUser(), table.getFieldCode(), projectId)
                    .getConcept());
        } catch (NoConfigForFieldException e) {
            log.warn("No configuration for field {} in project {}", table.getFieldCode(), projectId, e);
            return Optional.empty();
        }
    }

    /**
     * The concept a type name stands for, looked up by exact label within the field's vocabulary.
     * An ambiguous label is refused rather than resolved arbitrarily, since picking the wrong
     * concept would silently configure another type.
     */
    private Optional<Concept> findValueConcept(Long projectId, Concept fieldConcept, String typeName) {
        List<Concept> matches = conceptRepository.findAllByFieldContextAndExactLabel(
                fieldConcept.getId(), currentUser().getLang(), typeName);
        if (matches.size() == 1) return Optional.of(matches.get(0));
        if (matches.size() > 1) {
            log.warn("Type '{}' of project {} matches {} concepts of field {}; refusing to guess",
                    typeName, projectId, matches.size(), fieldConcept.getId());
        }
        return Optional.empty();
    }

    private List<ConceptAutocompleteDTO> fieldValues(Long projectId, ConfigurableTable table, @Nullable String input) {
        try {
            return fieldConfigurationService.fetchAutocomplete(currentUser(), table.getFieldCode(), input, projectId);
        } catch (NoConfigForFieldException e) {
            log.warn("No configuration for field {} in project {}", table.getFieldCode(), projectId, e);
            return List.of();
        }
    }

    private String labelOf(@Nullable Concept concept) {
        return concept == null ? "" : labelService.findLabelOf(concept, currentUser().getLang()).getLabel();
    }

    private TypeFieldFormConfig toDto(EffectiveField effective) {
        CustomField field = effective.field();
        FieldType type = typeOf(field);
        return TypeFieldFormConfig.builder()
                .name(field.getLabel())
                .type(type)
                .systemField(isSystemField(field))
                .active(effective.active())
                .mandatory(effective.mandatory())
                .institutionLocked(effective.institutionLocked())
                .configurable(type.isConfigurable())
                .sourceLabel(sourceOf(field))
                .build();
    }

    private TypeFieldFormConfig toDto(FieldFormConfig config) {
        return toDto(new EffectiveField(config.getField(), config, config.isMandatory()));
    }

    /**
     * Maps a custom field to the type the screen displays. {@link FieldType} covers what the
     * configuration screen knows how to show, which is less than the entity hierarchy expresses —
     * anything else falls back on {@link FieldType#TEXTE}.
     */
    private FieldType typeOf(CustomField field) {
        if (field instanceof CustomFieldInteger) return FieldType.NUMERIQUE;
        if (field instanceof CustomFieldMeasurement) return FieldType.MESURE;
        if (field instanceof CustomFieldSelectOneFromFieldCode
                || field instanceof CustomFieldSelectMultipleFromFieldCode
                || field instanceof CustomFieldSelectOneActionCode) return FieldType.VOCABULAIRE_CONTROLE;
        if (field instanceof CustomFieldSelectOneSpatialUnit
                || field instanceof CustomFieldSelectMultipleSpatialUnitTree
                || field instanceof CustomFieldSelectOneAddress) return FieldType.LIEU;
        if (field instanceof CustomFieldSelectOneActionUnit) return FieldType.PROJET;
        if (field instanceof CustomFieldSelectOneRecordingUnit
                || field instanceof CustomFieldSelectMultipleRecordingUnit) return FieldType.UNITE_ENREGISTREMENT;
        if (field instanceof CustomFieldStratigraphy) return FieldType.PARENTS;
        return FieldType.TEXTE;
    }

    /**
     * Where the values of a field come from: the field code of a controlled vocabulary, nothing for
     * the fields the user types into.
     */
    private String sourceOf(CustomField field) {
        if (field instanceof CustomFieldSelectOneFromFieldCode select) return orDash(select.getFieldCode());
        if (field instanceof CustomFieldSelectMultipleFromFieldCode select) return orDash(select.getFieldCode());
        return NO_SOURCE;
    }

    private String orDash(@Nullable String value) {
        return value == null || value.isBlank() ? NO_SOURCE : value;
    }

    private boolean isSystemField(CustomField field) {
        return Boolean.TRUE.equals(field.getIsSystemField());
    }

    private @NonNull UserInfo currentUser() {
        UserInfo info = ExecutionContextHolder.get();
        if (info == null) {
            throw new IllegalStateException("No user bound to the current thread; table field configuration " +
                    "is read in the context of a user, for their institution and language");
        }
        return info;
    }

    private Person currentPerson() {
        return personRepository.findById(currentUser().getUser().getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not a known person"));
    }
}
