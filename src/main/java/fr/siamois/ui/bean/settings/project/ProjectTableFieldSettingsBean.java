package fr.siamois.ui.bean.settings.project;

import fr.siamois.domain.models.events.LoginEvent;
import fr.siamois.domain.models.settings.tableconfig.*;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.FieldCatalogEntry;
import fr.siamois.domain.models.settings.tableconfig.FieldType;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeSummary;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;
import jakarta.faces.application.FacesMessage;
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

    private boolean pickerOpen;
    private String pickerQuery;

    private boolean drawerOpen;
    private String draftOriginalName;
    private String draftName;
    private FieldType draftType;
    private String draftDescription;

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
        pickerOpen = false;
        pickerQuery = null;
        closeDrawer();
        newTypeName = null;
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
        openPicker();
    }

    public void openPicker() {
        pickerQuery = "";
        pickerOpen = true;
    }

    public void closePicker() {
        pickerOpen = false;
    }

    /**
     * The catalog is read against the selected type, which the picker adds the field to: a field
     * that type already carries is not offered. The dialog is part of the page, so this is called
     * on every render, including before a type is selected.
     */
    public List<FieldCatalogEntry> getFieldCatalog() {
        if (project == null || selectedTable == null || selectedTypeName == null) return List.of();
        return tableFieldConfigService.searchFieldCatalog(project.getId(), selectedTable, selectedTypeName, pickerQuery);
    }

    public void pickExistingField(FieldCatalogEntry entry) {
        tableFieldConfigService.addExistingField(project.getId(), selectedTable, selectedTypeName, entry.getName());
        pickerOpen = false;
        loadConfigs();
    }

    public void openNewFieldDrawer() {
        pickerOpen = false;
        openDrawerForCreate();
    }

    public void openDrawerForCreate() {
        draftOriginalName = null;
        draftName = "";
        draftType = FieldType.TEXT;
        draftDescription = "";
        drawerOpen = true;
    }

    public void openDrawerForEdit(TypeFieldFormConfig field) {
        draftOriginalName = field.getName();
        draftName = field.getName();
        draftType = field.getType();
        draftDescription = field.getDescription();
        drawerOpen = true;
    }

    public void closeDrawer() {
        drawerOpen = false;
        draftOriginalName = null;
        draftName = null;
        draftType = null;
        draftDescription = null;
    }

    public void saveDrawer() {
        if (draftOriginalName == null) {
            tableFieldConfigService.createField(project.getId(), selectedTable, selectedTypeName, draftName, draftType, draftDescription);
        } else {
            tableFieldConfigService.updateField(project.getId(), selectedTable, selectedTypeName, draftOriginalName, draftName, draftType, draftDescription);
        }
        loadConfigs();
        closeDrawer();
    }

    public boolean isDraftCreateMode() {
        return draftOriginalName == null;
    }

    public FieldType[] getFieldTypeOptions() {
        return new FieldType[]{FieldType.TEXT, FieldType.INTEGER, FieldType.MEASUREMENT, FieldType.SELECT_ONE, FieldType.SELECT_MULTIPLE};
    }

    /**
     * The {@code p:selectOneMenu} binds to this String-backed pair rather than {@code draftType}
     * directly: every other {@code p:selectOneMenu} in this codebase does the same (see
     * {@code ProfileSettingsBean.FDefaultInstitutionId}/{@code FSelectedLang}), because relying on
     * JSF's implicit enum converter here doesn't reliably round-trip the selection on postback.
     */
    public String getDraftTypeName() {
        return draftType == null ? null : draftType.name();
    }

    public void setDraftTypeName(String name) {
        draftType = name == null ? null : FieldType.valueOf(name);
    }

    public List<String> completeConfigurableTypes(String query) {
        return tableFieldConfigService.listConfigurableTypes(project.getId(), selectedTable, query);
    }

    public void addConfiguration() {
        tableFieldConfigService.addConfiguration(project.getId(), selectedTable, newTypeName);
        typesForSelectedTable = tableFieldConfigService.listTypes(project.getId(), selectedTable);
        selectType(newTypeName);
        MessageUtils.displayMessage(langBean, FacesMessage.SEVERITY_INFO, "projectTables.tree.newConfigSuccess", newTypeName);
        newTypeName = null;
    }

}
