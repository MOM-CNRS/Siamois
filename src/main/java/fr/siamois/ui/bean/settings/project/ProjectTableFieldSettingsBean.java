package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.settings.tableconfig.AdditionalFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.SystemFieldConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeGeneralConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.event.TabChangeEvent;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Getter
@Setter
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ProjectTableFieldSettingsBean implements Serializable {

    public static final int TAB_GENERAL = 0;
    public static final int TAB_CHAMPS = 1;
    public static final int TAB_IDENTIFIANTS = 2;

    private final transient TableFieldConfigService tableFieldConfigService;
    private final LangBean langBean;

    private ActionUnitDTO project;
    private List<ConfigurableTable> tables = new ArrayList<>();
    private ConfigurableTable selectedTable;
    private List<TypeSummary> typesForSelectedTable = new ArrayList<>();
    private String selectedTypeName;
    private boolean treeOpen = true;
    private int activeTabIndex = TAB_CHAMPS;

    private TypeGeneralConfig generalConfig;
    private TypeFieldsConfig fieldsConfig;

    public ProjectTableFieldSettingsBean(TableFieldConfigService tableFieldConfigService, LangBean langBean) {
        this.tableFieldConfigService = tableFieldConfigService;
        this.langBean = langBean;
    }

    @EventListener(LoginEvent.class)
    public void reset() {
        project = null;
        tables = new ArrayList<>();
        selectedTable = null;
        typesForSelectedTable = new ArrayList<>();
        selectedTypeName = null;
        treeOpen = true;
        activeTabIndex = TAB_CHAMPS;
        generalConfig = null;
        fieldsConfig = null;
    }

    public void init(ActionUnitDTO project) {
        reset();
        this.project = project;
        tables = tableFieldConfigService.listTables();
        if (!tables.isEmpty()) {
            selectTable(tables.get(0));
        }
    }

    public void selectTable(ConfigurableTable table) {
        selectedTable = table;
        typesForSelectedTable = tableFieldConfigService.listTypes(project.getId(), table);
        String firstNonDefault = typesForSelectedTable.stream()
                .filter(t -> !t.isDefault())
                .map(TypeSummary::getName)
                .findFirst()
                .orElse(typesForSelectedTable.isEmpty() ? null : typesForSelectedTable.get(0).getName());
        selectType(firstNonDefault);
    }

    public void selectType(String typeName) {
        selectedTypeName = typeName;
        if (typeName == null) {
            generalConfig = null;
            fieldsConfig = null;
            return;
        }
        loadConfigs();
    }

    private void loadConfigs() {
        generalConfig = tableFieldConfigService.getGeneralConfig(project.getId(), selectedTable, selectedTypeName);
        fieldsConfig = tableFieldConfigService.getFieldsConfig(project.getId(), selectedTable, selectedTypeName);
    }

    public void toggleTree() {
        treeOpen = !treeOpen;
    }

    public boolean isSelectedTypeDefault() {
        return "_default".equals(selectedTypeName);
    }

    public long getHiddenSystemFieldCount() {
        if (fieldsConfig == null) return 0;
        return fieldsConfig.getSystemFields().stream().filter(f -> !f.isVisible()).count();
    }

    public int getTypeCountFor(ConfigurableTable table) {
        return tableFieldConfigService.listTypes(project.getId(), table).size();
    }

    public void onTabChange(TabChangeEvent<?> event) {
        // activeTabIndex is bound directly via p:tabView activeIndex, nothing else to do here
    }

    /**
     * The p:toggleSwitch controls bind directly (two-way) to the row's boolean property, so by the
     * time these listeners fire, the row already carries its new value — we just persist it.
     */
    public void toggleSystemFieldVisibility(SystemFieldConfig field) {
        tableFieldConfigService.setSystemFieldVisible(project.getId(), selectedTable, selectedTypeName, field.getName(), field.isVisible());
    }

    public void toggleSystemFieldRequired(SystemFieldConfig field) {
        tableFieldConfigService.setFieldRequired(project.getId(), selectedTable, selectedTypeName, field.getName(), true, field.isRequired());
    }

    public void toggleAdditionalFieldRequired(AdditionalFieldConfig field) {
        tableFieldConfigService.setFieldRequired(project.getId(), selectedTable, selectedTypeName, field.getName(), false, field.isRequired());
    }

    public void deleteAdditionalField(String fieldName) {
        tableFieldConfigService.deleteAdditionalField(project.getId(), selectedTable, selectedTypeName, fieldName);
        loadConfigs();
    }

    public void addField() {
        tableFieldConfigService.addAdditionalField(project.getId(), selectedTable, selectedTypeName);
        loadConfigs();
    }

    public void saveGeneralConfig() {
        tableFieldConfigService.saveGeneralConfig(project.getId(), selectedTable, generalConfig);
        MessageUtils.displayInfoMessage(langBean, "projectTables.general.saveSuccess");
    }

}
