package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
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

    private TypeFormConfig formConfig;
    private TypeFieldsConfig fieldsConfig;

    /** Value picked in the "add a configuration" dialog. */
    private String newTypeName;

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
        formConfig = null;
        fieldsConfig = null;
        newTypeName = null;
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
            formConfig = null;
            fieldsConfig = null;
            return;
        }
        loadConfigs();
    }

    private void loadConfigs() {
        formConfig = tableFieldConfigService.getFormConfig(project.getId(), selectedTable, selectedTypeName);
        fieldsConfig = tableFieldConfigService.getFieldsConfig(project.getId(), selectedTable, selectedTypeName);
    }

    /**
     * Values of the table's type field that can still be configured. Called by the picker of the
     * "add a configuration" dialog on every keystroke, hence the query parameter.
     */
    public List<String> completeConfigurableTypes(String query) {
        if (selectedTable == null) return List.of();
        return tableFieldConfigService.listConfigurableTypes(project.getId(), selectedTable, query);
    }

    /**
     * Creates the configuration of the picked type and opens it, so the user lands on what they
     * just created rather than on the type they were reading.
     */
    public void addConfiguration() {
        if (newTypeName == null || newTypeName.isBlank()) {
            MessageUtils.displayErrorMessage(langBean, "projectTables.tree.newConfigRequired");
            return;
        }
        TypeSummary created = tableFieldConfigService.addConfiguration(project.getId(), selectedTable, newTypeName);
        typesForSelectedTable = tableFieldConfigService.listTypes(project.getId(), selectedTable);
        selectType(created.getName());
        newTypeName = null;
        MessageUtils.displayInfoMessage(langBean, "projectTables.tree.newConfigSuccess", created.getName());
    }

    public void toggleTree() {
        treeOpen = !treeOpen;
    }

    public boolean isSelectedTypeDefault() {
        return "_default".equals(selectedTypeName);
    }

    public long getHiddenSystemFieldCount() {
        if (fieldsConfig == null) return 0;
        return fieldsConfig.getFields().stream().filter(f -> f.isSystemField() && !f.isActive()).count();
    }

    /**
     * Caption to display for a field. A system field carries a message key rather than a caption,
     * whereas a field added from this screen carries what the user typed — same convention as
     * {@code SpatialUnitFieldBean#resolveCustomFieldLabel}.
     * <p>
     * The untranslated name stays the field's identity: it is what the service is called back with
     * to activate, require or delete it.
     */
    public String resolveFieldLabel(TypeFieldFormConfig field) {
        if (field.isSystemField()) {
            return langBean.msg(field.getName());
        }
        return field.getName();
    }

    public List<TypeFieldFormConfig> getSystemFields() {
        if (fieldsConfig == null) return List.of();
        return fieldsConfig.getFields().stream().filter(TypeFieldFormConfig::isSystemField).toList();
    }

    public List<TypeFieldFormConfig> getAdditionalFields() {
        if (fieldsConfig == null) return List.of();
        return fieldsConfig.getFields().stream().filter(f -> !f.isSystemField()).toList();
    }

    public int getTypeCountFor(ConfigurableTable table) {
        return tableFieldConfigService.listTypes(project.getId(), table).size();
    }

    public void onTabChange(TabChangeEvent<?> event) {
        // activeTabIndex is bound directly via p:tabView activeIndex, nothing else to do here
    }

    /**
     * The p:toggleSwitch controls bind directly (two-way) to the row's boolean property, so by the
     * time these listeners fire, the row already carries its new value — we just persist it. A
     * field marked institutionLocked ignores the write (enforced server-side by the service too).
     */
    public void toggleFieldActive(TypeFieldFormConfig field) {
        tableFieldConfigService.setFieldActive(project.getId(), selectedTable, selectedTypeName, field.getName(), field.isActive());
    }

    public void toggleFieldMandatory(TypeFieldFormConfig field) {
        tableFieldConfigService.setFieldMandatory(project.getId(), selectedTable, selectedTypeName, field.getName(), field.isMandatory());
    }

    public void deleteAdditionalField(String fieldName) {
        tableFieldConfigService.deleteAdditionalField(project.getId(), selectedTable, selectedTypeName, fieldName);
        loadConfigs();
    }

    public void addField() {
        tableFieldConfigService.addAdditionalField(project.getId(), selectedTable, selectedTypeName);
        loadConfigs();
    }

    public void saveFormConfig() {
        tableFieldConfigService.saveFormConfig(project.getId(), selectedTable, formConfig);
        MessageUtils.displayInfoMessage(langBean, "projectTables.general.saveSuccess");
    }

}
