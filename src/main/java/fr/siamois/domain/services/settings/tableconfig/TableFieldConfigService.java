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
