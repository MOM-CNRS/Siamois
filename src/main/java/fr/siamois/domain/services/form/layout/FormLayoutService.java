package fr.siamois.domain.services.form.layout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.form.config.FieldFormConfig;
import fr.siamois.domain.models.form.config.FieldWidth;
import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.config.FormConfigGroup;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.layout.FormLayout;
import fr.siamois.domain.models.form.rules.FieldRules;
import fr.siamois.domain.models.form.rules.FieldRulesJson;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.LayoutRow;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import fr.siamois.domain.services.form.rules.FieldRulesValidator;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.infrastructure.database.repositories.form.config.FieldFormConfigRepository;
import fr.siamois.infrastructure.database.repositories.form.config.FormConfigGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the stored layout of a (project, table, type): the groups of its {@code FormConfig} and the
 * fields each one holds, in order. A configuration without any group has no layout yet (see
 * {@code EffectiveFormResolver} for what applies then).
 * <p>
 * Nothing is merged: the layout found is the layout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormLayoutService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TableFieldConfigService tableFieldConfigService;
    private final FormConfigGroupRepository groupRepository;
    private final FieldFormConfigRepository fieldFormConfigRepository;
    private final FieldRulesValidator rulesValidator;

    /**
     * @param typeConceptId the type's concept id, or null for the default configuration
     * @return the stored layout that applies to the type, if its configuration — or, failing that,
     * the default one — has one
     */
    @Transactional(readOnly = true)
    public Optional<FormLayout> storedLayout(Long projectId, ConfigurableTable table, Long typeConceptId) {
        Optional<FormLayout> own = tableFieldConfigService.findFormConfig(projectId, table, typeConceptId)
                .flatMap(this::layoutOf);
        if (own.isPresent() || typeConceptId == null) {
            return own;
        }
        return tableFieldConfigService.findFormConfig(projectId, table, (Long) null).flatMap(this::layoutOf);
    }

    /** The layout stored for a form configuration, empty when it has no group. */
    @Transactional(readOnly = true)
    public Optional<FormLayout> layoutOf(FormConfig formConfig) {
        List<FormConfigGroup> groups = groupRepository.findAllByFormConfigIdOrderByPosition(formConfig.getId());
        if (groups.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, List<FormLayout.Item>> itemsByGroup = new LinkedHashMap<>();
        groups.forEach(g -> itemsByGroup.put(g.getId(), new ArrayList<>()));
        for (FieldFormConfig ffc : fieldFormConfigRepository.findAllByFormConfigId(formConfig.getId())) {
            List<FormLayout.Item> items = ffc.getGroup() == null ? null : itemsByGroup.get(ffc.getGroup().getId());
            if (items == null) continue;
            items.add(new FormLayout.Item(
                    (CustomField) Hibernate.unproxy(ffc.getField()),
                    ffc.getWidth() == null ? FieldWidth.QUARTER : ffc.getWidth(),
                    ffc.isActive(),
                    ffc.isMandatory(),
                    ffc.isInstitutionLocked(),
                    parseRules(ffc)));
        }
        List<FormLayout.Group> out = groups.stream()
                .map(g -> new FormLayout.Group(g.getId(), g.getLabel(), itemsByGroup.get(g.getId())))
                .toList();
        return Optional.of(new FormLayout(out));
    }

    // ---- Editing ----------------------------------------------------------------------------

    /** What a group looks like once edited: its stored id (null when new), label and field names. */
    public record GroupSpec(Long id, String label, List<String> fieldNames) {
    }

    /**
     * The layout the configuration screen shows for a type: its own if it has one, else the default
     * configuration's, else what the form effectively was before layouts were stored.
     */
    @Transactional(readOnly = true)
    public List<LayoutRow> rowsOf(Long projectId, ConfigurableTable table, String typeName) {
        List<LayoutRow> rows = new ArrayList<>();
        for (FormLayout.Group group : effectiveLayout(projectId, table, typeName).groups()) {
            rows.add(LayoutRow.header(group.id(), group.label()));
            for (FormLayout.Item item : group.items()) {
                rows.add(LayoutRow.of(tableFieldConfigService.describe(item.field(), item.active(), item.mandatory(),
                        item.institutionLocked()), item.width(), item.rules() != null && !item.rules().isEmpty()));
            }
        }
        return rows;
    }

    /** A field of the type's form, for what needs its definition (the values a rule can compare it to). */
    @Transactional(readOnly = true)
    public Optional<CustomField> fieldOf(Long projectId, ConfigurableTable table, String typeName, long fieldId) {
        return effectiveLayout(projectId, table, typeName).groups().stream()
                .flatMap(g -> g.items().stream())
                .map(FormLayout.Item::field)
                .filter(field -> field.getId() == fieldId)
                .findFirst();
    }

    /** The rules stored for a field of the type's form, none when it has none. */
    @Transactional(readOnly = true)
    public FieldRules rulesOf(Long projectId, ConfigurableTable table, String typeName, long fieldId) {
        return effectiveLayout(projectId, table, typeName).groups().stream()
                .flatMap(g -> g.items().stream())
                .filter(item -> item.field().getId() == fieldId)
                .map(FormLayout.Item::rules)
                .findFirst().orElse(FieldRules.NONE);
    }

    /**
     * Replaces, as a whole, the rules of a field. They are validated against the fields of the type's
     * form first ({@link InvalidRulesException}); a field locked by the institution is left untouched.
     */
    @Transactional
    public void saveRules(Long projectId, ConfigurableTable table, String typeName, long fieldId, FieldRules rules) {
        FormConfig config = ensureOwnLayoutInternal(projectId, table, typeName);
        List<FieldFormConfig> rows = fieldFormConfigRepository.findAllByFormConfigId(config.getId());
        FieldFormConfig target = rows.stream().filter(r -> r.getField().getId() == fieldId).findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("No field " + fieldId + " on type " + typeName));
        if (target.isInstitutionLocked()) {
            log.debug("Field {} is locked by the institution, its rules cannot be changed", fieldId);
            return;
        }
        Map<Long, CustomField> fields = new HashMap<>();
        rows.forEach(r -> fields.put(r.getField().getId(), (CustomField) Hibernate.unproxy(r.getField())));
        List<FieldRulesValidator.Issue> issues = rulesValidator.validate(rules, fieldId, fields);
        if (!issues.isEmpty()) {
            throw new InvalidRulesException(issues);
        }
        target.setRules(rulesJson(rules));
        fieldFormConfigRepository.save(target);
    }

    private FormLayout effectiveLayout(Long projectId, ConfigurableTable table, String typeName) {
        Optional<FormConfig> own = tableFieldConfigService.findFormConfig(projectId, table, typeName);
        Optional<FormLayout> stored = own.flatMap(this::layoutOf);
        if (stored.isPresent()) return stored.get();
        return tableFieldConfigService.legacyLayout(projectId, table, typeName);
    }

    /**
     * Lays out every existing configuration of the table that has no layout yet, each with what its
     * form effectively was, so nothing changes on screen and each type from then on carries its own
     * complete layout. A no-op once done. Called before the first edit of a layout.
     */
    @Transactional
    public void ensureLayouts(Long projectId, ConfigurableTable table) {
        ensureLayoutsInternal(projectId, table);
    }

    private void ensureLayoutsInternal(Long projectId, ConfigurableTable table) {
        for (TypeSummary type : tableFieldConfigService.listTypes(projectId, table)) {
            Optional<FormConfig> config = tableFieldConfigService.findFormConfig(projectId, table, type.getName());
            if (config.isPresent() && groupRepository.countByFormConfigId(config.get().getId()) == 0) {
                writeLayout(config.get(), tableFieldConfigService.legacyLayout(projectId, table, type.getName()));
            }
        }
    }

    /**
     * The type's configuration, created and laid out when it has none: a new type starts from the
     * default's layout when there is one.
     */
    @Transactional
    public FormConfig ensureOwnLayout(Long projectId, ConfigurableTable table, String typeName) {
        return ensureOwnLayoutInternal(projectId, table, typeName);
    }

    private FormConfig ensureOwnLayoutInternal(Long projectId, ConfigurableTable table, String typeName) {
        ensureLayoutsInternal(projectId, table);
        Optional<FormConfig> existing = tableFieldConfigService.findFormConfig(projectId, table, typeName);
        if (existing.isPresent()) {
            return existing.get();
        }
        FormLayout layout = effectiveLayout(projectId, table, typeName);
        FormConfig created = tableFieldConfigService.createOrGetFormConfig(projectId, table, typeName)
                .orElseThrow(() -> new IllegalStateException("No configuration can be created for type " + typeName));
        writeLayout(created, layout);
        return created;
    }

    /**
     * Stores the arrangement the user made: groups in order (renamed, added, dropped) and the fields
     * of each in order. A dropped group must not hold fields; one that still does (a field missing
     * from the arrangement) gives them to the last group.
     */
    @Transactional
    public void saveArrangement(Long projectId, ConfigurableTable table, String typeName, List<GroupSpec> specs) {
        if (specs.isEmpty()) {
            throw new IllegalArgumentException("A layout needs at least one group");
        }
        FormConfig config = ensureOwnLayoutInternal(projectId, table, typeName);
        Map<Long, FormConfigGroup> stored = new HashMap<>();
        groupRepository.findAllByFormConfigIdOrderByPosition(config.getId()).forEach(g -> stored.put(g.getId(), g));
        Map<String, FieldFormConfig> byName = new HashMap<>();
        List<FieldFormConfig> rows = fieldFormConfigRepository.findAllByFormConfigId(config.getId());
        rows.forEach(row -> byName.put(row.getField().getLabel(), row));

        Set<Long> kept = new HashSet<>();
        Set<String> placed = new HashSet<>();
        FormConfigGroup last = null;
        int groupPosition = 0;
        for (GroupSpec spec : specs) {
            FormConfigGroup group = spec.id() == null ? null : stored.get(spec.id());
            if (group == null) {
                group = new FormConfigGroup(config, spec.label(), groupPosition);
            }
            group.setLabel(spec.label());
            group.setPosition(groupPosition++);
            group = groupRepository.save(group);
            kept.add(group.getId());
            last = group;
            placeFields(spec, group, byName, placed);
        }
        giveOrphansTo(last, rows, kept, placed);
        stored.values().stream().filter(g -> !kept.contains(g.getId())).forEach(groupRepository::delete);
    }

    /** The fields a group spec names, in order, each placed once in the whole arrangement. */
    private void placeFields(GroupSpec spec, FormConfigGroup group, Map<String, FieldFormConfig> byName, Set<String> placed) {
        int position = 0;
        for (String name : spec.fieldNames()) {
            FieldFormConfig row = byName.get(name);
            if (row != null && placed.add(name)) {
                row.setGroup(group);
                row.setPosition(++position);
                fieldFormConfigRepository.save(row);
            }
        }
    }

    /** A field left in a dropped group, and missing from the arrangement, goes to the last group. */
    private void giveOrphansTo(FormConfigGroup last, List<FieldFormConfig> rows, Set<Long> kept, Set<String> placed) {
        final Long lastId = last.getId();
        int position = (int) rows.stream().filter(r -> r.getGroup() != null && lastId.equals(r.getGroup().getId())).count();
        for (FieldFormConfig row : rows) {
            boolean orphan = row.getGroup() != null && !kept.contains(row.getGroup().getId());
            if (orphan && !placed.contains(row.getField().getLabel())) {
                row.setGroup(last);
                row.setPosition(++position);
                fieldFormConfigRepository.save(row);
            }
        }
    }

    @Transactional
    public void setFieldWidth(Long projectId, ConfigurableTable table, String typeName, String fieldName, FieldWidth width) {
        FormConfig config = ensureOwnLayoutInternal(projectId, table, typeName);
        fieldFormConfigRepository.findAllByFormConfigId(config.getId()).stream()
                .filter(row -> fieldName.equals(row.getField().getLabel()))
                .findFirst()
                .ifPresentOrElse(row -> {
                    row.setWidth(width);
                    fieldFormConfigRepository.save(row);
                }, () -> log.warn("No field '{}' on type '{}' of table {} in project {}", fieldName, typeName, table, projectId));
    }

    /** Writes a layout into a configuration that has none, reusing the field rows it already has. */
    private void writeLayout(FormConfig config, FormLayout layout) {
        Map<Long, FieldFormConfig> existing = new HashMap<>();
        fieldFormConfigRepository.findAllByFormConfigId(config.getId())
                .forEach(row -> existing.put(row.getField().getId(), row));
        int groupPosition = 0;
        for (FormLayout.Group group : layout.groups()) {
            FormConfigGroup stored = groupRepository.save(new FormConfigGroup(config, group.label(), groupPosition++));
            int position = 0;
            for (FormLayout.Item item : group.items()) {
                FieldFormConfig row = existing.remove(item.field().getId());
                if (row == null) {
                    row = new FieldFormConfig();
                    row.setField(item.field());
                    row.setFormConfig(config);
                }
                row.setGroup(stored);
                row.setWidth(item.width());
                row.setPosition(++position);
                row.setActive(item.active());
                row.setMandatory(item.mandatory());
                row.setInstitutionLocked(item.institutionLocked());
                row.setRules(rulesJson(item.rules()));
                fieldFormConfigRepository.save(row);
            }
        }
    }

    private static String rulesJson(FieldRules rules) {
        if (rules == null || rules.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(FieldRulesJson.toWire(rules));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Rules cannot be written", e);
        }
    }

    private FieldRules parseRules(FieldFormConfig ffc) {
        if (ffc.getRules() == null || ffc.getRules().isBlank()) {
            return FieldRules.NONE;
        }
        try {
            return FieldRulesJson.fromJson(MAPPER.readTree(ffc.getRules()));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Unreadable rules on field {} of form config {}: {}", ffc.getField().getId(),
                    ffc.getFormConfig().getId(), e.getMessage());
            return FieldRules.NONE;
        }
    }
}
