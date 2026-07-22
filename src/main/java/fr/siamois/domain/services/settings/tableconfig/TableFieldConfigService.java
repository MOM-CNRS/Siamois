package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
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
     * Persists changes to a type's general configuration.
     *
     * @param projectId the project (action unit) this configuration is scoped to
     * @param table     the table the type belongs to
     * @param config    the configuration to save; {@link TypeFormConfig#getTypeName()} identifies
     *                  which type it applies to
     */
    void saveFormConfig(Long projectId, ConfigurableTable table, TypeFormConfig config);

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
}
