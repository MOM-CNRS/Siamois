package fr.siamois.domain.services.settings.tableconfig;

import fr.siamois.domain.models.settings.tableconfig.AdditionalFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeGeneralConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;

import java.util.List;

/**
 * Configuration of recording tables, their types and the fields available on each type, for a given project.
 */
public interface TableFieldConfigService {

    List<ConfigurableTable> listTables();

    List<TypeSummary> listTypes(Long projectId, ConfigurableTable table);

    TypeGeneralConfig getGeneralConfig(Long projectId, ConfigurableTable table, String typeName);

    void saveGeneralConfig(Long projectId, ConfigurableTable table, TypeGeneralConfig config);

    TypeFieldsConfig getFieldsConfig(Long projectId, ConfigurableTable table, String typeName);

    void setSystemFieldVisible(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean visible);

    void setFieldRequired(Long projectId, ConfigurableTable table, String typeName, String fieldName, boolean isSystemField, boolean required);

    AdditionalFieldConfig addAdditionalField(Long projectId, ConfigurableTable table, String typeName);

    void deleteAdditionalField(Long projectId, ConfigurableTable table, String typeName, String fieldName);
}
