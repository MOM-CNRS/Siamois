package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.*;

import java.util.List;
import java.util.Optional;

/**
 * Configuration of tables, their types and the fields available on each type, for a given project.
 */
public interface TableFieldConfigService {

    String DEFAULT_TYPE = "_default";

    /**
     * Lists every table that can be configured (UE, Mobilier, Phase, Contenant).
     *
     * @return the configurable tables, in a stable display order
     */
    List<ConfigurableTable> listTables();

    /**
     * Lists the types a table is configured for: {@code _default}, plus every type somebody
     * explicitly created a configuration for through
     * {@link #addConfiguration(Long, ConfigurableTable, String)}.
     * <p>
     * A type the project never configured is deliberately absent: every value of the type field
     * would otherwise need a configuration of its own in every project of every institution, for
     * nothing — such a value is served by the {@code _default} configuration.
     *
     * @param projectId the project (action unit) these types are scoped to
     * @param table     the table whose types are listed
     * @return the table's configured types, {@code _default} first
     */
    List<TypeSummary> listTypes(Long projectId, ConfigurableTable table);

    /**
     * Lists the values of the table's type field that could still be given a configuration, i.e.
     * the values of its vocabulary minus the ones already configured. Meant to back the type picker
     * of the "add a configuration" action.
     *
     * @param projectId the project (action unit) the configuration would be scoped to
     * @param table     the table whose type field is read
     * @param input     what the user typed, to narrow the vocabulary down; null or blank lists all
     * @return the names of the values that have no configuration yet
     */
    List<String> listConfigurableTypes(Long projectId, ConfigurableTable table, String input);

    /**
     * Creates the configuration of a type, so it can diverge from the default one. Does nothing
     * beyond returning the existing type when it is already configured.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the name of the type field value to configure
     * @return the newly configured type
     */
    TypeSummary addConfiguration(Long projectId, ConfigurableTable table, String typeName);

    /**
     * Reads the general (non-field) configuration of a type.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @return a copy of the type's general configuration
     */
    TypeFormConfig getFormConfig(Long projectId, ConfigurableTable table, String typeName);

    /**
     * Persists changes to a type's general configuration.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param config    the configuration to save; {@link TypeFormConfig#getTypeName()} identifies
     *                  which type it applies to
     */
    void saveFormConfig(Long projectId, ConfigurableTable table, TypeFormConfig config);

    /**
     * Reads the system and additional fields configured for a type. System fields come in the order
     * the form lays them out; the additional ones follow, in the display order set through
     * {@link #reorderAdditionalFields(Long, ConfigurableTable, String, List)}.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @return a copy of the type's field configuration
     */
    TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, String typeName);

    /**
     * The real additional (non-system) fields active for a type — the entities needed to render
     * them, not the display DTO returned by {@link #getFieldsConfig} — in the display order set
     * through {@link #reorderAdditionalFields(Long, ConfigurableTable, String, List)}. This is the
     * order the data entry screens lay the additional fields out in, so the two screens agree.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @return the active additional fields configured for the type, in display order
     */
    List<CustomField> getActiveAdditionalFields(Long projectId, ConfigurableTable table, String typeName);

    /**
     * Same as {@link #getFieldsConfig(Long, ConfigurableTable, String)}, but keyed directly on the
     * type's {@link fr.siamois.domain.models.vocabulary.Concept} id instead of its label — avoids the
     * label round-trip ({@link #findFormConfig(Long, ConfigurableTable, String)} resolving a label
     * back to a concept), which is fragile for a caller that already holds the concept (e.g. an API
     * request whose {@code lang} may differ from the label's language).
     *
     * @param projectId     the project (action unit) this configuration is scoped to
     * @param table         the table the type belongs to
     * @param typeConceptId the type's concept id, or {@code null} for the default configuration
     * @return a copy of the type's field configuration
     */
    TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, Long typeConceptId);

    /**
     * Concept-id-keyed equivalent of {@link #getActiveAdditionalFields(Long, ConfigurableTable, String)}.
     *
     * @param projectId     the project (action unit) this configuration is scoped to
     * @param table         the table the type belongs to
     * @param typeConceptId the type's concept id, or {@code null} for the default configuration
     * @return the active additional fields configured for the type, in display order
     */
    List<CustomField> getActiveAdditionalFields(Long projectId, ConfigurableTable table, Long typeConceptId);

    /**
     * Sets the order the additional fields of a type are displayed in, both on the configuration
     * screen and on the data entry screens that lay them out.
     * <p>
     * The order is a property of the type's own configuration: applying it to a type that inherits
     * additional fields from the default configuration gives that type its own configuration rows
     * for them, so the reorder does not leak to every other type — the same rule
     * {@link #setFieldActive} and {@link #setFieldMandatory} follow.
     * <p>
     * Names that are unknown to the type, or that name a system field, are ignored: system fields
     * are laid out by the form, not by this screen. An additional field the list omits keeps its
     * position relative to the other omitted ones, after the listed ones.
     *
     * @param projectId         the project (action unit) this configuration is scoped to
     * @param table             the table the type belongs to
     * @param typeName          the type's name, or {@code _default}
     * @param orderedFieldNames the names of the type's additional fields, in the order they must be
     *                          displayed in
     */
    void reorderAdditionalFields(Long projectId, ConfigurableTable table, String typeName, List<String> orderedFieldNames);

    /**
     * Resolves the {@link FormConfig} that applies to a type, if one was ever materialized for it
     * (see {@link #addConfiguration(Long, ConfigurableTable, String)} / the field-editing actions
     * that create one lazily). Read-only: unlike {@link #getFieldsConfig}, this never creates a row.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @return the type's form configuration, empty if none was ever materialized
     */
    Optional<FormConfig> findFormConfig(Long projectId, ConfigurableTable table, String typeName);

    /**
     * Same as {@link #findFormConfig}, but materializes the configuration when the type has none —
     * for answers that need something to hang on even though nobody ever opened the settings screen
     * for that type, such as a measurement field created straight from a unit's form.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @return the type's form configuration, empty when the project has no vocabulary configured for
     * the table's type field, or the type is not one of that field's values — there is then nothing
     * to scope a configuration to
     */
    Optional<FormConfig> createOrGetFormConfig(Long projectId, ConfigurableTable table, String typeConceptId);

    /**
     * Activates or deactivates a field for a type. No-op when the target field is
     * {@code institutionLocked}: a locked field's {@code active} state can't be overridden at the
     * project level (only future institution-level settings screens may change it).
     * To be defined: what happens if user try to deactivate a field already in use?
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @param fieldName the name of the field to update (system or additional)
     * @param active    the new active state
     */
    void setFieldActive(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean active);

    /**
     * Marks a field as mandatory or optional for a type. No-op when the target field is
     * {@code institutionLocked}: a locked field's {@code mandatory} state can't be overridden at
     * the project level (only future institution-level settings screens may change it).
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @param fieldName the name of the field to update (system or additional)
     * @param mandatory the new mandatory state
     */
    void setFieldMandatory(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean mandatory);

    /**
     * Removes an additional field from a type. No-op if the named field is a system field (system
     * fields can be deactivated but never deleted) or doesn't exist.
     * To be defined: what happens if user try to delete an additional field already in use?
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @param fieldName the name of the additional field to remove
     */
    void deleteAdditionalField(Long projectId, ConfigurableTable table, String typeName, String fieldName);

    /**
     * Searches the reusable field catalog offered by the "reuse an existing field" picker.
     * <p>
     * The catalog holds the non-system fields of the current user's institution only, minus the
     * ones already configured on the type: those are offered by the picker for nothing, since
     * {@link #addExistingField(Long, ConfigurableTable, String, String)} would leave the type
     * unchanged.
     *
     * @param projectId the project (action unit) the catalog is scoped to
     * @param table     the table the fields would be added to
     * @param typeName  the type's name, or {@code _default}; its fields are excluded from the result
     * @param query     a free-text filter matched against field name and description,
     *                  case-insensitively; blank or {@code null} returns the full catalog
     * @return the matching catalog entries
     */
    List<FieldCatalogEntry> searchFieldCatalog(Long projectId, ConfigurableTable table, String typeName, String query);

    /**
     * Creates a new additional field on a type from user-provided name, type and description.
     * Name and description are saved in as label and definition of the concept
     *      * linked to the custom field.
     * @param projectId   the project (action unit) this configuration is scoped to
     * @param table       the table the type belongs to
     * @param typeName    the type's name, or {@code _default}
     * @param name        the new field's name
     * @param type        the new field's type
     * @param description the new field's description
     * @return a copy of the newly created field
     */
    TypeFieldFormConfig createField(Long projectId, ConfigurableTable table, String typeName, String name, FieldType type, String description);

    /**
     * Adds a field from the reusable catalog to a type as an additional field. No-op (returns the
     * existing field) if a field with that name is already present on the type.
     *
     * @param projectId       the project (action unit) this configuration is scoped to
     * @param table           the table the type belongs to
     * @param typeName        the type's name, or {@code _default}
     * @param catalogFieldName the name of the catalog entry to reuse
     * @return a copy of the field now configured on the type
     */
    TypeFieldFormConfig addExistingField(Long projectId, ConfigurableTable table, String typeName, String catalogFieldName);

    /**
     * Modifies an existing additional field's name, type and description in place. No-op if the
     * named field is a system field or doesn't exist. Name and description are saved in as label and definition of the concept
     * linked to the custom field.
     *
     * @param projectId   the project (action unit) this configuration is scoped to
     * @param table       the table the type belongs to
     * @param typeName    the type's name, or {@code _default}
     * @param fieldName   the current name of the additional field to update
     * @param newName     the field's new name
     * @param newType     the field's new type
     * @param description the field's new description
     * @return a copy of the updated field
     */
    TypeFieldFormConfig updateField(Long projectId, ConfigurableTable table, String typeName, String fieldName, String newName, FieldType newType, String description);
}