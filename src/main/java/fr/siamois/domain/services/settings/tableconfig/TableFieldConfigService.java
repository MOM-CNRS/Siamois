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

    List<ConfigurableTable> listTables();

    List<TypeSummary> listTypes(Long projectId, ConfigurableTable table);

    TypeFormConfig getFormConfig(Long projectId, ConfigurableTable table, String typeName);

    void saveFormConfig(Long projectId, ConfigurableTable table, TypeFormConfig config);

    TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, String typeName);

    /**
     * No-op when the target field is {@code institutionLocked}: active/mandatory can't be
     * overridden at the project level for a locked field.
     */
    void setFieldActive(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean active);

    void setFieldMandatory(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean mandatory);

    TypeFieldFormConfig addAdditionalField(Long projectId, ConfigurableTable table, String typeName);

    void deleteAdditionalField(Long projectId, ConfigurableTable table, String typeName, String fieldName);
}
