package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldCatalogEntry;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;

import java.util.List;

/**
 * Configuration of tables, their types and the fields available on each type, for a given project.
 */
public interface TableFieldConfigService {

    /**
     * Lists every table that can be configured (UE, Mobilier, Phase, Contenant).
     *
     * @return the configurable tables, in a stable display order
     */
    List<ConfigurableTable> listTables();

    /**
     * Lists the types defined for a table, including the {@code _default} type.
     *
     * @param projectId the project (action unit) these types are scoped to
     * @param table     the table whose types are listed
     * @return the table's types, {@code _default} first
     */
    List<TypeSummary> listTypes(Long projectId, ConfigurableTable table);

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
     * Reads the system and additional fields configured for a type.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @return a copy of the type's field configuration
     */
    TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, String typeName);

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
     * Adds a new additional field to a type, seeded with default values (name "Nouveau champ",
     * type {@code TEXTE}, not mandatory), and returns it so the caller can display/edit it further.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param typeName  the type's name, or {@code _default}
     * @return a copy of the newly created field
     */
    TypeFieldFormConfig addAdditionalField(Long projectId, ConfigurableTable table, String typeName);

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
     *
     * @param projectId the project (action unit) the catalog is scoped to
     * @param query     a free-text filter matched against field name and description,
     *                  case-insensitively; blank or {@code null} returns the full catalog
     * @return the matching catalog entries
     */
    List<FieldCatalogEntry> searchFieldCatalog(Long projectId, String query);

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
