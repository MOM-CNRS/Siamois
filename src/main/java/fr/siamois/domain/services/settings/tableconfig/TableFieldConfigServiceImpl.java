package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.vocabulary.NoConfigForFieldException;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigGroup;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionCode;
import fr.siamois.domain.models.form.customfield.actionunit.CustomFieldSelectOneActionUnit;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectMultipleRecordingUnit;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldSelectOneRecordingUnit;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectMultipleSpatialUnitTree;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneAddress;
import fr.siamois.domain.models.form.customfield.spatialunit.CustomFieldSelectOneSpatialUnit;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConceptFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultipleFromFieldCode;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOne;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.LocalizedConceptData;
import fr.siamois.domain.services.form.layout.FormLayoutSeeds;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldAnswerRepository;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigGroupRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.utils.context.ExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;
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

    private static final String NO_SOURCE = "—";
    private static final int DEFAULT_MIN_CODE = 1;
    private static final int DEFAULT_MAX_CODE = 999;
    public static final String OF_PROJECT = " of project ";
    public static final String NO_VOCABULARY_CONFIGURED_FOR_FIELD = "No vocabulary configured for field ";

    private final FieldConfigurationService fieldConfigurationService;
    private final LabelService labelService;
    private final ConceptService conceptService;
    private final ActionUnitRepository actionUnitRepository;
    private final ConceptRepository conceptRepository;
    private final FormConfigRepository formConfigRepository;
    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldAnswerRepository customFieldAnswerRepository;
    private final PersonRepository personRepository;
    private final ObjectProvider<TableFieldConfigServiceImpl> selfProvider;
    private final FormLayoutSeeds formLayoutSeeds;
    private final FormConfigGroupRepository formConfigGroupRepository;

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
        Set<String> candidates = fieldValues(projectId, table, input).stream()
                .map(value -> value.getConceptLabelToDisplay().getLabel())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    @Override
    public List<Concept> listConfiguredTypeConcepts(Long projectId, ConfigurableTable table) {
        Optional<Concept> fieldConcept = findFieldConcept(projectId, table);
        if (fieldConcept.isEmpty()) return List.of();

        return formConfigRepository.findAllByActionUnitAndField(projectId, fieldConcept.get().getId()).stream()
                .map(FormConfig::getValueConcept)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Set<String> configuredTypeNames(Long projectId, ConfigurableTable table) {
        return listConfiguredTypeConcepts(projectId, table).stream()
                .map(this::labelOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    @Transactional(readOnly = true)
    public TypeFormConfig getFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        boolean isDefault = DEFAULT_TYPE.equals(typeName);
        Optional<FormConfig> stored = findFormConfig(projectId, table, typeName);
        Optional<Concept> valueConcept = stored
                .map(FormConfig::getValueConcept);
        if (valueConcept.isEmpty() && !isDefault) {
            valueConcept = findFieldConcept(projectId, table)
                    .flatMap(fieldConcept -> findValueConcept(projectId, fieldConcept, typeName));
        }
        FormConfig identifiers = stored.orElseGet(() -> findFormConfig(projectId, table, DEFAULT_TYPE).orElse(null));
        return TypeFormConfig.builder()
                .typeName(typeName)
                .definition(valueConcept.map(this::definitionOf).orElse(""))
                .identifierFormat(identifiers == null ? table.getDefaultIdentifierFormat() : identifiers.getIdentifierFormat())
                .minCode(identifiers == null ? DEFAULT_MIN_CODE : identifiers.getMinCode())
                .maxCode(identifiers == null ? DEFAULT_MAX_CODE : identifiers.getMaxCode())
                .build();
    }

    /**
     * Materializes the type's configuration so later field edits have somewhere to be written.
     */
    @Override
    @Transactional
    public void saveFormConfig(Long projectId, ConfigurableTable table, TypeFormConfig config) {
        FormConfig stored = requireFormConfig(projectId, table, config.getTypeName());
        // Existing callers use this method only to materialize a row and omit identifier values.
        if (config.getIdentifierFormat() == null) return;
        if (config.getIdentifierFormat().isBlank()) {
            throw new IllegalArgumentException("Identifier format is required");
        }
        if (config.getMinCode() < 0 || config.getMaxCode() < config.getMinCode()) {
            throw new IllegalArgumentException("Invalid identifier range");
        }
        stored.setIdentifierFormat(config.getIdentifierFormat());
        stored.setMinCode(config.getMinCode());
        stored.setMaxCode(config.getMaxCode());
        formConfigRepository.save(stored);
    }

    @Override
    @Transactional
    public void saveFormConfig(Long projectId, ConfigurableTable table, Long typeConceptId, TypeFormConfig config) {
        FormConfig stored = createOrGetFormConfig(projectId, table, typeConceptId)
                .orElseThrow(() -> new IllegalStateException(
                        NO_VOCABULARY_CONFIGURED_FOR_FIELD + table.getFieldCode() + OF_PROJECT + projectId));
        if (config.getIdentifierFormat() == null || config.getIdentifierFormat().isBlank()) {
            throw new IllegalArgumentException("Identifier format is required");
        }
        if (config.getMinCode() < 0 || config.getMaxCode() < config.getMinCode()) {
            throw new IllegalArgumentException("Invalid identifier range");
        }
        stored.setIdentifierFormat(config.getIdentifierFormat());
        stored.setMinCode(config.getMinCode());
        stored.setMaxCode(config.getMaxCode());
        formConfigRepository.save(stored);
    }

    @Override
    @Transactional
    public FormConfig resolveIdentifierConfig(Long projectId, ConfigurableTable table, Long typeConceptId) {
        if (typeConceptId != null) {
            Optional<FormConfig> typed = findFormConfig(projectId, table, typeConceptId);
            if (typed.isPresent()) return typed.get();
        }
        return createOrGetFormConfig(projectId, table, (Long) null)
                .orElseThrow(() -> new IllegalStateException("No type field configured for " + table));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTypeFieldConfigured(Long projectId, ConfigurableTable table) {
        return findFieldConcept(projectId, table).isPresent();
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
    @Transactional(readOnly = true)
    public List<CustomField> getActiveAdditionalFields(Long projectId, ConfigurableTable table, String typeName) {
        return effectiveFields(projectId, table, typeName).values().stream()
                .filter(field -> !isSystemField(field.field()) && field.active())
                .map(EffectiveField::field)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomField> findField(Long projectId, ConfigurableTable table, String typeName, String fieldName) {
        return effectiveFields(projectId, table, typeName).values().stream()
                .filter(field -> fieldName.equals(field.field().getLabel()))
                .map(EffectiveField::field)
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, Long typeConceptId) {
        TypeFieldsConfig config = new TypeFieldsConfig();
        config.setFields(new ArrayList<>(effectiveFields(projectId, table, typeConceptId).values().stream()
                .map(this::toDto)
                .toList()));
        return config;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomField> getActiveAdditionalFields(Long projectId, ConfigurableTable table, Long typeConceptId) {
        return effectiveFields(projectId, table, typeConceptId).values().stream()
                .filter(field -> !isSystemField(field.field()) && field.active())
                .map(EffectiveField::field)
                .toList();
    }

    @Override
    @Transactional
    public void setFieldActive(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean active) {
        applyFieldChange(projectId, table, typeName, fieldName, config -> config.setActive(active));
    }

    @Override
    @Transactional
    public void setFieldMandatory(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean mandatory) {
        applyFieldChange(projectId, table, typeName, fieldName, config -> config.setMandatory(mandatory));
    }

    @Override
    @Transactional
    public void reorderAdditionalFields(Long projectId, ConfigurableTable table, String typeName, List<String> orderedFieldNames) {
        FormConfig owner = requireFormConfig(projectId, table, typeName);
        List<FieldFormConfig> fieldConfigs = fieldFormConfigRepository.findAllByFormConfigId(owner.getId());

        for (int i = 0; i < orderedFieldNames.size(); i++) {
            String fieldName = orderedFieldNames.get(i);
            Optional<FieldFormConfig> optFieldFormConfig = fieldConfigs.stream()
                    .filter(ffc -> ffc.getField().getLabel().equalsIgnoreCase(fieldName))
                    .findFirst();
            if (optFieldFormConfig.isPresent()) {
                FieldFormConfig toUpdate = optFieldFormConfig.get();
                toUpdate.setPosition(i + 1);
                fieldFormConfigRepository.save(toUpdate);
            }
        }
    }


    /**
     * Unlinks an additional field from the type, unless the project already holds answers for it —
     * those answers would be stranded, so the field is kept and the caller told about it.
     * <p>
     * Every link that puts the field on the type is dropped, the type's own and the one it inherits
     * from the default configuration alike: dropping only the type's own would leave the field
     * inherited, so it would come straight back on the very next read. A field the type only
     * inherits is therefore removed from every type that inherits it, which is what removing a field
     * the screen presents as one row means.
     */
    @Override
    @Transactional
    public boolean deleteAdditionalField(Long projectId, ConfigurableTable table, String typeName, String fieldName) {
        List<FieldFormConfig> links = linksOfAdditionalField(projectId, table, typeName, fieldName);
        if (links.isEmpty()) {
            warnNoFieldOnType(projectId, table, typeName, fieldName);
            return true;
        }

        CustomField field = links.get(0).getField();
        if (customFieldAnswerRepository.countByFieldIdAndProjectId(field.getId(), projectId) > 0) {
            log.debug("Field '{}' is answered in project {}; it is kept on type '{}' of table {}",
                    fieldName, projectId, typeName, table);
            return false;
        }

        links.forEach(fieldFormConfigRepository::delete);
        return true;
    }

    /**
     * The stored configurations that put an additional field on a type: the type's own and the one
     * it inherits from the default configuration. {@link #storedFields} keeps only the winning one
     * of the two, which is all reading a field needs, whereas removing it has to reach both.
     */
    private List<FieldFormConfig> linksOfAdditionalField(Long projectId, ConfigurableTable table, String typeName, String fieldName) {
        List<FieldFormConfig> links = new ArrayList<>();
        if (!DEFAULT_TYPE.equals(typeName)) {
            collectLinksOfAdditionalField(findFormConfig(projectId, table, DEFAULT_TYPE), fieldName, links);
        }
        collectLinksOfAdditionalField(findFormConfig(projectId, table, typeName), fieldName, links);
        return links;
    }

    private void collectLinksOfAdditionalField(Optional<FormConfig> formConfig, String fieldName, List<FieldFormConfig> into) {
        formConfig.ifPresent(config -> fieldFormConfigRepository.findAllByFormConfigId(config.getId()).stream()
                .filter(link -> !isSystemField(link.getField()))
                .filter(link -> fieldName.equalsIgnoreCase(link.getField().getLabel()))
                .forEach(into::add));
    }

    /**
     * Reusable, non-system custom fields whose name or description matches the query, offered by the
     * "reuse an existing field" picker. The reuse pool is the custom fields of the current
     * institution, de-duplicated by label and minus the ones the type already carries — offering a
     * field that is already configured would be a no-op click; the description shown is the field's
     * {@code hint}.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FieldCatalogEntry> searchFieldCatalog(Long projectId, ConfigurableTable table, String typeName, String query) {
        String needle = query == null ? "" : query.toLowerCase().trim();
        Set<String> configured = configuredFieldNames(projectId, table, typeName);
        return reusableCatalogFields().stream()
                .filter(field -> !configured.contains(field.getLabel()))
                .filter(field -> needle.isBlank()
                        || matches(field.getLabel(), needle)
                        || matches(field.getHint(), needle))
                .map(field -> FieldCatalogEntry.builder()
                        .name(field.getLabel())
                        .type(typeOf(field))
                        .description(field.getHint())
                        .build())
                .toList();
    }

    private Set<String> configuredFieldNames(Long projectId, ConfigurableTable table, String typeName) {
        return effectiveFields(projectId, table, typeName).values().stream()
                .map(field -> field.field().getLabel())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Creates a new additional field on a type from a user-provided name, type and description.
     * The name is stored as the custom field's label and the description as its hint; a custom field
     * subclass matching {@code type} is instantiated so the persisted {@code answer_type}
     * discriminator stays in step with the type shown in the UI.
     */
    @Override
    @Transactional
    public TypeFieldFormConfig createField(Long projectId, ConfigurableTable table, String typeName,
                                           String name, FieldType type, String description) {
        SetupReplacementLabelResult result = configureReplacementLabel(projectId, table, typeName, name, type, description);
        return toDto(linkField(result.savedReplacement, result.owner, true, false));
    }

    /**
     * Adds an already-defined custom field, picked from the catalog by its name, to a type. No-op
     * (returns the field as already configured) when a field with that name is present on the type.
     */
    @Override
    @Transactional
    public TypeFieldFormConfig addExistingField(Long projectId, ConfigurableTable table, String typeName,
                                                String catalogFieldName) {
        FormConfig formConfig = requireFormConfig(projectId, table, typeName);
        Optional<EffectiveField> present = effectiveFields(projectId, table, typeName).values().stream()
                .filter(field -> catalogFieldName.equals(field.field().getLabel()))
                .findFirst();
        if (present.isPresent()) {
            return toDto(present.get());
        }
        CustomField field = reusableCatalogFields().stream()
                .filter(candidate -> catalogFieldName.equals(candidate.getLabel()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown catalog field: " + catalogFieldName));
        return toDto(linkField(field, formConfig, true, false));
    }

    /**
     * Modifies an additional field's name, type and description for a type. No-op (returns
     * {@code null}) when the named field is a system field or doesn't exist.
     * <p>
     * A same-type edit updates the custom field entity in place. A type change can't move an
     * existing row to another {@code answer_type} discriminator, so it creates a fresh field of the
     * new type, repoints this type's configuration to it (keeping its active/mandatory state), and
     * deletes the old field once nothing else references it.
     */
    @Override
    @Transactional
    public TypeFieldFormConfig updateField(Long projectId, ConfigurableTable table, String typeName,
                                           String fieldName, String newName, FieldType newType, String description) {
        Optional<EffectiveField> target = effectiveFields(projectId, table, typeName).values().stream()
                .filter(field -> fieldName.equals(field.field().getLabel()))
                .findFirst();
        if (target.isEmpty()) {
            warnNoFieldOnType(projectId, table, typeName, fieldName);
            return null;
        }
        EffectiveField effective = target.get();
        CustomField current = effective.field();
        if (isSystemField(current)) {
            log.debug("Field '{}' is a system field; its definition is not editable from this screen", fieldName);
            return null;
        }

        if (typeOf(current) == newType) {
            current.setLabel(newName);
            current.setHint(description);
            customFieldRepository.save(current);
            return toDto(new EffectiveField(current, effective.stored(), effective.requiredByForm()));
        }

        SetupReplacementLabelResult result = configureReplacementLabel(projectId, table, typeName, newName, newType, description);

        boolean owned = ownedBy(effective, result.owner());
        if (owned) {
            fieldFormConfigRepository.delete(effective.stored());
        }
        FieldFormConfig link = linkField(result.savedReplacement(), result.owner(), effective.active(), effective.mandatory());
        if (owned && effective.stored().getGroup() != null) {
            copyPlacement(effective.stored(), link);
        }
        if (fieldFormConfigRepository.countByFieldId(current.getId()) == 0) {
            customFieldRepository.delete(current);
        }
        return toDto(link);
    }

    private @NonNull TableFieldConfigServiceImpl.SetupReplacementLabelResult configureReplacementLabel(Long projectId, ConfigurableTable table, String typeName, String newName, FieldType newType, String description) {
        FormConfig owner = requireFormConfig(projectId, table, typeName);
        CustomField replacement = newCustomFieldOfType(newType);
        replacement.setLabel(newName);
        replacement.setIsSystemField(false);
        replacement.setHint(description);
        replacement.setAuthor(currentPerson());
        CustomField savedReplacement = customFieldRepository.save(replacement);
        return new SetupReplacementLabelResult(owner, savedReplacement);
    }

    private record SetupReplacementLabelResult(FormConfig owner, CustomField savedReplacement) {
    }

    private boolean matches(@Nullable String value, String lowerCaseNeedle) {
        return value != null && value.toLowerCase().contains(lowerCaseNeedle);
    }

    private FieldFormConfig linkField(CustomField field, FormConfig formConfig, boolean active, boolean mandatory) {
        FieldFormConfig link = new FieldFormConfig();
        link.setField(field);
        link.setFormConfig(formConfig);
        link.setActive(active);
        link.setMandatory(mandatory);
        link.setInstitutionLocked(false);
        placeInLayout(link, formConfig);
        return fieldFormConfigRepository.save(link);
    }

    /**
     * A configuration that has a layout shows only the fields that are in one of its groups, so a
     * field linked to it lands at the end of the "additional fields" group (created when missing).
     * A configuration without a layout is left alone: its fields are laid out when it gets one.
     */
    private void placeInLayout(FieldFormConfig link, FormConfig formConfig) {
        List<FormConfigGroup> groups = formConfigGroupRepository.findAllByFormConfigIdOrderByPosition(formConfig.getId());
        if (groups == null || groups.isEmpty()) return;
        FormConfigGroup target = groups.stream()
                .filter(group -> FormLayout.ADDITIONAL_GROUP_LABEL.equals(group.getLabel()))
                .findFirst()
                .orElseGet(() -> formConfigGroupRepository.save(new FormConfigGroup(
                        formConfig, FormLayout.ADDITIONAL_GROUP_LABEL, groups.get(groups.size() - 1).getPosition() + 1)));
        int last = fieldFormConfigRepository.findAllByFormConfigId(formConfig.getId()).stream()
                .filter(other -> other.getGroup() != null && target.getId().equals(other.getGroup().getId()))
                .mapToInt(FieldFormConfig::getPosition)
                .max().orElse(0);
        link.setGroup(target);
        link.setWidth(FieldWidth.QUARTER);
        link.setPosition(last + 1);
    }

    private void copyPlacement(FieldFormConfig from, FieldFormConfig to) {
        to.setGroup(from.getGroup());
        to.setWidth(from.getWidth());
        to.setPosition(from.getPosition());
        to.setRules(from.getRules());
        fieldFormConfigRepository.save(to);
    }

    private List<CustomField> reusableCatalogFields() {
        Map<String, CustomField> byLabel = new LinkedHashMap<>();
        for (CustomField field : customFieldRepository.findAllReusableByInstitution(currentUser().getInstitution().getId())) {
            if (field.getLabel() != null) {
                byLabel.putIfAbsent(field.getLabel(), field);
            }
        }
        return List.copyOf(byLabel.values());
    }

    private CustomField newCustomFieldOfType(FieldType type) {
        return switch (type) {
            case INTEGER -> CustomFieldInteger.builder().build();
            case MEASUREMENT -> CustomFieldMeasurement.builder().build();
            case SELECT_ONE -> new CustomFieldSelectOne();
            case SELECT_MULTIPLE -> new CustomFieldSelectMultiple();
            default -> CustomFieldText.builder().isTextArea(false).build();
        };
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

    private Map<Long, EffectiveField> effectiveFields(Long projectId, ConfigurableTable table, String typeName) {
        return effectiveFields(table, storedFields(projectId, table, typeName));
    }

    /** Concept-id-keyed equivalent of {@link #effectiveFields(Long, ConfigurableTable, String)}. */
    private Map<Long, EffectiveField> effectiveFields(Long projectId, ConfigurableTable table, Long typeConceptId) {
        return effectiveFields(table, storedFields(projectId, table, typeConceptId));
    }

    private Map<Long, EffectiveField> effectiveFields(ConfigurableTable table, Map<Long, FieldFormConfig> stored) {
        Map<Long, EffectiveField> fields = new LinkedHashMap<>();

        for (FormField formField : systemFields(table)) {
            CustomField field = formField.field();
            fields.put(field.getId(), new EffectiveField(field, stored.get(field.getId()), formField.required()));
        }
        stored.forEach((fieldId, config) ->
                // FieldFormConfig#field is lazy: unproxy it so field.getClass() matches the
                // concrete subclass everywhere else (CustomFieldAnswerFactory dispatches on it).
                fields.computeIfAbsent(fieldId, id -> new EffectiveField((CustomField) Hibernate.unproxy(config.getField()), config, false)));
        return fields;
    }

    private Map<Long, FieldFormConfig> storedFields(Long projectId, ConfigurableTable table, String typeName) {
        Map<Long, FieldFormConfig> fields = new LinkedHashMap<>();
        if (!DEFAULT_TYPE.equals(typeName)) {
            collectFields(findFormConfig(projectId, table, DEFAULT_TYPE), fields);
        }
        collectFields(findFormConfig(projectId, table, typeName), fields);
        return fields;
    }

    /** Concept-id-keyed equivalent of {@link #storedFields(Long, ConfigurableTable, String)}. */
    private Map<Long, FieldFormConfig> storedFields(Long projectId, ConfigurableTable table, Long typeConceptId) {
        Map<Long, FieldFormConfig> fields = new LinkedHashMap<>();
        if (typeConceptId != null) {
            collectFields(findFormConfig(projectId, table, (Long) null), fields);
        }
        collectFields(findFormConfig(projectId, table, typeConceptId), fields);
        return fields;
    }

    private void collectFields(Optional<FormConfig> formConfig, Map<Long, FieldFormConfig> into) {
        formConfig.ifPresent(config -> fieldFormConfigRepository.findAllByFormConfigId(config.getId())
                .forEach(field -> into.put(field.getField().getId(), field)));
    }

    /** A field the table lays out, with the requiredness its definition declares for it. */
    private record FormField(CustomField field, boolean required) {
    }

    private List<FormField> systemFields(ConfigurableTable table) {
        Map<String, CustomField> persisted = new HashMap<>();
        customFieldRepository.findAllSystemFields()
                .forEach(field -> persisted.putIfAbsent(SystemFieldCatalog.identityOf(field), field));

        List<FormField> fields = new ArrayList<>();
        Set<Long> required = formLayoutSeeds.requiredFieldIds(table);
        for (CustomField declared : SystemFieldCatalog.sharedFieldsOf(table)) {
            // The owning project is set at creation and never configured (SystemFieldCatalog#isOwningProject).
            if (SystemFieldCatalog.isOwningProject(declared)) continue;
            CustomField field = persisted.get(SystemFieldCatalog.identityOf(declared));
            if (field == null) {
                log.warn("System field '{}' of table {} has no row; it was defined after the last startup",
                        declared.getLabel(), table);
            } else {
                fields.add(new FormField(field, required.contains(declared.getId())));
            }
        }
        return fields;
    }

    /**
     * The layout a configuration without a stored one effectively has: the table's initial layout
     * carrying the type's (and the default's) active/mandatory flags, plus the additional fields in a
     * group of their own. It is what the form showed before layouts were stored, and what gets
     * written when a configuration is first edited.
     */
    @Override
    @Transactional(readOnly = true)
    public FormLayout legacyLayout(Long projectId, ConfigurableTable table, String typeName) {
        Map<Long, EffectiveField> effective = effectiveFields(projectId, table, typeName);
        Map<String, EffectiveField> systemByIdentity = new HashMap<>();
        effective.values().stream()
                .filter(field -> isSystemField(field.field()))
                .forEach(field -> systemByIdentity.put(SystemFieldCatalog.identityOf(field.field()), field));

        Set<Long> placed = new HashSet<>();
        List<FormLayout.Group> groups = new ArrayList<>();
        for (FormLayout.Group seedGroup : formLayoutSeeds.layoutOf(table).groups()) {
            List<FormLayout.Item> items = new ArrayList<>();
            for (FormLayout.Item seed : seedGroup.items()) {
                EffectiveField field = systemByIdentity.get(SystemFieldCatalog.identityOf(seed.field()));
                if (field == null) continue;
                placed.add(field.field().getId());
                items.add(new FormLayout.Item(field.field(), seed.width(), field.active(), field.mandatory(),
                        field.institutionLocked(), seed.rules()));
            }
            groups.add(new FormLayout.Group(null, seedGroup.label(), items));
        }
        List<FormLayout.Item> additional = effective.values().stream()
                .filter(field -> !placed.contains(field.field().getId()) && !isSystemField(field.field()))
                .sorted(Comparator.comparingInt(field -> field.stored() == null ? 0 : field.stored().getPosition()))
                .map(field -> new FormLayout.Item(field.field(), FieldWidth.QUARTER, field.active(), field.mandatory(),
                        field.institutionLocked(), FieldRules.NONE))
                .toList();
        if (!additional.isEmpty()) {
            groups.add(new FormLayout.Group(null, FormLayout.ADDITIONAL_GROUP_LABEL, additional));
        }
        return new FormLayout(groups);
    }

    @Override
    public Optional<FormConfig> findFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        Optional<Concept> fieldConcept = findFieldConcept(projectId, table);
        if (fieldConcept.isEmpty()) return Optional.empty();

        if (DEFAULT_TYPE.equals(typeName)) {
            return formConfigRepository.findDefaultByActionUnitAndField(projectId, fieldConcept.get().getId());
        }
        return findValueConcept(projectId, fieldConcept.get(), typeName)
                .flatMap(value -> formConfigRepository.findByActionUnitAndFieldAndValue(
                        projectId, fieldConcept.get().getId(), value.getId()));
    }

    @Override
    public Optional<FormConfig> findFormConfig(Long projectId, ConfigurableTable table, Long typeConceptId) {
        Optional<Concept> fieldConcept = findFieldConcept(projectId, table);
        if (fieldConcept.isEmpty()) return Optional.empty();

        if (typeConceptId == null) {
            return formConfigRepository.findDefaultByActionUnitAndField(projectId, fieldConcept.get().getId());
        }
        return formConfigRepository.findByActionUnitAndFieldAndValue(projectId, fieldConcept.get().getId(), typeConceptId);
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
    @Transactional
    public Optional<FormConfig> createOrGetFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        Optional<FormConfig> existing = findFormConfig(projectId, table, typeName);
        if (existing.isPresent()) {
            return existing;
        }

        Optional<Concept> fieldConcept = findFieldConcept(projectId, table);
        if (fieldConcept.isEmpty()
                || !DEFAULT_TYPE.equals(typeName) && findValueConcept(projectId, fieldConcept.get(), typeName).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(createOrRecoverFormConfig(projectId, table, typeName));
    }

    @Override
    public Optional<FormConfig> createOrGetFormConfig(Long projectId, ConfigurableTable table, Long typeConceptId) {
        Optional<FormConfig> existing = findFormConfig(projectId, table, typeConceptId);
        if (existing.isPresent()) {
            return existing;
        }
        if (findFieldConcept(projectId, table).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(createOrRecoverFormConfig(projectId, table, typeConceptId));
    }

    private void applyFieldChange(Long projectId, ConfigurableTable table, String typeName, String fieldName,
                                  Consumer<FieldFormConfig> change) {
        Optional<EffectiveField> target = effectiveFields(projectId, table, typeName).values().stream()
                .filter(field -> fieldName.equals(field.field().getLabel()))
                .findFirst();

        if (target.isEmpty()) {
            warnNoFieldOnType(projectId, table, typeName, fieldName);
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
        assert config != null;
        fieldFormConfigRepository.save(config);
    }

    private static void warnNoFieldOnType(Long projectId, ConfigurableTable table, String typeName, String fieldName) {
        log.warn("No field '{}' on type '{}' of table {} in project {}",
                fieldName, typeName, table, projectId);
    }

    private boolean ownedBy(EffectiveField field, FormConfig owner) {
        return field.stored() != null && owner.getId().equals(field.stored().getFormConfig().getId());
    }

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
                .orElseGet(() -> createOrRecoverFormConfig(projectId, table, typeName));
    }

    /**
     * Materializes a {@link FormConfig} row, tolerating a concurrent request doing the same thing.
     * The row is created lazily (see the class javadoc), so two requests racing to configure the
     * same type for the first time can both find {@link #findFormConfig} empty and both attempt to
     * insert it. {@code uk_form_config_scope} catches that for a type-specific row, but not for the
     * default one (its {@code valueConcept} is null, and a UNIQUE constraint never considers two
     * NULLs equal) — either way, the loser here recovers by re-reading the row the winner committed,
     * instead of surfacing the constraint violation to the user or leaving a stray duplicate.
     * <p>
     * The insert runs in its own transaction (via {@link #createFormConfigInNewTransaction}) so a
     * constraint violation only rolls back that insert, not the whole calling transaction.
     */
    private FormConfig createOrRecoverFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        try {
            return selfProvider.getObject().createFormConfigInNewTransaction(projectId, table, typeName);
        } catch (DataIntegrityViolationException e) {
            return findFormConfig(projectId, table, typeName)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Same as {@link #createOrRecoverFormConfig(Long, ConfigurableTable, String)}, for the
     * type-concept-id-keyed lookup used by {@link #createOrGetFormConfig(Long, ConfigurableTable, Long)}.
     */
    private FormConfig createOrRecoverFormConfig(Long projectId, ConfigurableTable table, Long typeConceptId) {
        try {
            return selfProvider.getObject().createFormConfigInNewTransaction(projectId, table, typeConceptId);
        } catch (DataIntegrityViolationException e) {
            return findFormConfig(projectId, table, typeConceptId)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Runs {@link #createFormConfig(Long, ConfigurableTable, String)} in its own transaction, so a
     * unique-constraint violation caused by a concurrent insert of the same row only rolls back this
     * insert — the caller's transaction (and any work already done in it) is left intact and can
     * recover by re-reading the row instead. Must be called through {@link #selfProvider} (a plain
     * {@code this} call would bypass the transactional proxy and run in the caller's transaction).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FormConfig createFormConfigInNewTransaction(Long projectId, ConfigurableTable table, String typeName) {
        return createFormConfig(projectId, table, typeName);
    }

    /**
     * Same as {@link #createFormConfigInNewTransaction(Long, ConfigurableTable, String)}, for the
     * type-concept-id-keyed creation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FormConfig createFormConfigInNewTransaction(Long projectId, ConfigurableTable table, Long typeConceptId) {
        return createFormConfig(projectId, table, typeConceptId);
    }

    private FormConfig createFormConfig(Long projectId, ConfigurableTable table, String typeName) {
        ActionUnit project = actionUnitRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Unknown project: " + projectId));
        Concept fieldConcept = findFieldConcept(projectId, table)
                .orElseThrow(() -> new IllegalStateException(
                        NO_VOCABULARY_CONFIGURED_FOR_FIELD + table.getFieldCode() + OF_PROJECT + projectId));

        FormConfig config = new FormConfig();
        config.setActionUnit(project);
        config.setInstitution(project.getCreatedByInstitution());
        config.setFieldConcept(fieldConcept);
        config.setFieldConfigs(new ArrayList<>());
        initializeIdentifierConfig(config, table, projectId, DEFAULT_TYPE.equals(typeName));
        if (!DEFAULT_TYPE.equals(typeName)) {
            config.setValueConcept(findValueConcept(projectId, fieldConcept, typeName)
                    .orElseThrow(() -> new NoSuchElementException(
                            "Unknown type '" + typeName + "' for table " + table)));
        }
        return formConfigRepository.save(config);
    }

    private FormConfig createFormConfig(Long projectId, ConfigurableTable table, Long typeConceptId) {
        ActionUnit project = actionUnitRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Unknown project: " + projectId));
        Concept fieldConcept = findFieldConcept(projectId, table)
                .orElseThrow(() -> new IllegalStateException(
                        NO_VOCABULARY_CONFIGURED_FOR_FIELD + table.getFieldCode() + OF_PROJECT + projectId));

        FormConfig config = new FormConfig();
        config.setActionUnit(project);
        config.setInstitution(project.getCreatedByInstitution());
        config.setFieldConcept(fieldConcept);
        config.setFieldConfigs(new ArrayList<>());
        initializeIdentifierConfig(config, table, projectId, typeConceptId == null);
        if (typeConceptId != null) {
            config.setValueConcept(conceptRepository.findById(typeConceptId)
                    .orElseThrow(() -> new NoSuchElementException("Unknown concept id " + typeConceptId)));
        }
        return formConfigRepository.save(config);
    }

    /**
     * Seeds the identifier configuration of a row being created. A type inherits the identifier
     * configuration of the project's default configuration — the very one it was reading through
     * {@link #getFormConfig(Long, ConfigurableTable, String)} until it got a row of its own — so
     * materializing the row, whatever the change that triggers it, does not silently move the type
     * back onto the built-in format and bounds.
     */
    private void initializeIdentifierConfig(FormConfig config, ConfigurableTable table, Long projectId,
                                            boolean isDefault) {
        Optional<FormConfig> inherited = isDefault
                ? Optional.empty()
                : findFormConfig(projectId, table, (Long) null);
        config.setIdentifierFormat(inherited
                .map(FormConfig::getIdentifierFormat)
                .orElseGet(table::getDefaultIdentifierFormat));
        config.setMinCode(inherited.map(FormConfig::getMinCode).orElse(DEFAULT_MIN_CODE));
        config.setMaxCode(inherited.map(FormConfig::getMaxCode).orElse(DEFAULT_MAX_CODE));
    }

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

    /**
     * The table's own type field, resolved as an entity rather than by its field code — needed to
     * honor a branch/collection restriction configured on it through the field-settings drawer, which
     * {@link fr.siamois.domain.services.vocabulary.FieldConfigurationService#fetchAutocomplete(CustomFieldConcept, String, Long)}
     * reads before falling back to the field-code configuration.
     */
    private Optional<CustomFieldConcept> findTypeField(ConfigurableTable table) {
        return systemFields(table).stream()
                .map(FormField::field)
                .filter(CustomFieldConceptFromFieldCode.class::isInstance)
                .map(CustomFieldConceptFromFieldCode.class::cast)
                .filter(field -> table.getFieldCode().equals(field.getFieldCode()))
                .map(CustomFieldConcept.class::cast)
                .findFirst();
    }

    private List<ConceptAutocompleteDTO> fieldValues(Long projectId, ConfigurableTable table, @Nullable String input) {
        try {
            Optional<CustomFieldConcept> typeField = findTypeField(table);
            if (typeField.isPresent()) {
                return fieldConfigurationService.fetchAutocomplete(typeField.get(), input, projectId);
            }
            return fieldConfigurationService.fetchAutocomplete(currentUser(), table.getFieldCode(), input, projectId);
        } catch (NoConfigForFieldException e) {
            log.warn("No configuration for field {} in project {}", table.getFieldCode(), projectId, e);
            return List.of();
        }
    }

    private String labelOf(@Nullable Concept concept) {
        return concept == null ? "" : labelService.findLabelOf(concept, currentUser().getLang()).getLabel();
    }

    private String definitionOf(@Nullable Concept concept) {
        if (concept == null) return "";
        LocalizedConceptData data = conceptService.getLocalizedConceptDataByConceptAndLangCode(concept, currentUser().getLang());
        return data == null || data.getDefinition() == null ? "" : data.getDefinition();
    }

    private TypeFieldFormConfig toDto(EffectiveField effective) {
        return describe(effective.field(), effective.active(), effective.mandatory(), effective.institutionLocked());
    }

    @Override
    public TypeFieldFormConfig describe(CustomField field, boolean active, boolean mandatory, boolean institutionLocked) {
        FieldType type = typeOf(field);
        return TypeFieldFormConfig.builder()
                .id(field.getId())
                .ruleFamily(fr.siamois.domain.models.form.rules.RuleFieldFamily.of(field))
                .name(field.getLabel())
                .type(type)
                .systemField(isSystemField(field))
                .active(active)
                .mandatory(mandatory)
                .institutionLocked(institutionLocked)
                .configurable(type.isConfigurable())
                .sourceLabel(sourceOf(field))
                .valueBinding(field.getValueBinding())
                .build();
    }

    private TypeFieldFormConfig toDto(FieldFormConfig config) {
        return toDto(new EffectiveField(config.getField(), config, config.isMandatory()));
    }

    private FieldType typeOf(CustomField field) {
        if (field instanceof CustomFieldInteger) return FieldType.INTEGER;
        if (field instanceof CustomFieldMeasurement) return FieldType.MEASUREMENT;
        if (field instanceof CustomFieldSelectOneFromFieldCode
                || field instanceof CustomFieldSelectOneActionCode) return FieldType.SELECT_ONE;
        if (field instanceof CustomFieldSelectMultipleFromFieldCode) return FieldType.SELECT_MULTIPLE;
        if (field instanceof CustomFieldSelectOneSpatialUnit
                || field instanceof CustomFieldSelectMultipleSpatialUnitTree
                || field instanceof CustomFieldSelectOneAddress) return FieldType.SELECT_ONE_SPATIAL_UNIT;
        if (field instanceof CustomFieldSelectOneActionUnit) return FieldType.PROJET;
        if (field instanceof CustomFieldSelectOneRecordingUnit
                || field instanceof CustomFieldSelectMultipleRecordingUnit) return FieldType.SELECT_ONE_RECORDING_UNIT;
        if (field instanceof CustomFieldSelectOne) return FieldType.SELECT_ONE;
        if (field instanceof CustomFieldSelectMultiple) return FieldType.SELECT_MULTIPLE;
        return FieldType.TEXT;
    }

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
