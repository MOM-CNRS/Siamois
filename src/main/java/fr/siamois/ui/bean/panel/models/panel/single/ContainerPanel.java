package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.form.customform.CustomFormComposer;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldFormConfig;
import fr.siamois.domain.models.settings.tableconfig.TypeFieldsConfig;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.settings.tableconfig.TableFieldConfigService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.primefaces.model.menu.DefaultMenuItem;
import org.primefaces.model.menu.DefaultMenuModel;
import org.primefaces.model.menu.MenuModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ContainerPanel extends AbstractSingleEntityPanel<ContainerDTO> implements Serializable {

    private final transient ContainerService containerService;
    private final transient RedirectBean redirectBean;
    private final transient TableFieldConfigService tableFieldConfigService;
    private final transient LabelService labelService;
    private final transient ProfilePermissionService profilePermissionService;

    @Override
    protected boolean documentExistsInUnitByHash(ContainerDTO unit, String hash) {
        return false;
    }

    @Override
    protected void addDocumentToUnit(Document doc, ContainerDTO unit) {
        // not yet supported for containers
    }

    protected ContainerPanel(ApplicationContext context) {
        super("common.entity.container",
                "bi bi-box-seam",
                "siamois-panel container-panel single-panel",
                context);
        this.containerService = context.getBean(ContainerService.class);
        this.redirectBean = context.getBean(RedirectBean.class);
        this.tableFieldConfigService = context.getBean(TableFieldConfigService.class);
        this.labelService = context.getBean(LabelService.class);
        this.profilePermissionService = context.getBean(ProfilePermissionService.class);
    }

    public String entityRessourceUri() {
        return "/container/" + unitId;
    }

    @Override
    public boolean canUserEditUnit() {
        return unit != null && profilePermissionService.hasContainerWritePermission(sessionSettingsBean.getUserInfo(), unit);
    }

    @Override
    public String displayHeader() {
        return "/panel/header/containerPanelHeader.xhtml";
    }

    @Override
    public UnitKind getCreationUnitKind() {
        return UnitKind.CONTAINER;
    }

    @Override
    public void refreshUnit() {
        errorMessage = null;
        unit = null;

        try {
            unit = containerService.findById(unitId);
            this.titleCodeOrTitle = unit.getIdentifier();
            initForms(true);
        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load container: " + e.getMessage();
        }

        documents = List.of();
    }

    @Override
    protected String currentIdentifierValue() {
        return unit.getIdentifier();
    }

    @Override
    protected boolean persistIdentifierEdit(String trimmed) {
        if (trimmed.isEmpty()) {
            MessageUtils.displayWarnMessage(langBean, "container.error.identifier.blank");
            return false;
        }

        String previous = unit.getIdentifier();
        unit.setIdentifier(trimmed);

        if (containerService.identifierAlreadyExistInAction(unit)) {
            unit.setIdentifier(previous);
            MessageUtils.displayWarnMessage(langBean, "container.error.identifier.alreadyExists");
            return false;
        }

        try {
            containerService.save(unit);
            this.titleCodeOrTitle = unit.getIdentifier();
            return true;
        } catch (RuntimeException e) {
            unit.setIdentifier(previous);
            MessageUtils.displayErrorMessage(langBean, "common.entity.container.updateFailed", unit.getIdentifier());
            return false;
        }
    }

    @Override
    public void init() {
        try {
            activeTabIndex = 0;

            if (unitId == null) {
                this.errorMessage = "The ID of the container must be defined";
                return;
            }

            refreshUnit();

            if (this.unit == null) {
                log.error("The Container page should not be accessed without ID or by direct page path");
                errorMessage = "The Container page should not be accessed without ID or by direct page path";
            }
        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load container: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<PersonDTO> authorsAvailable() {
        return List.of();
    }

    @Override
    protected String getFocusPath(Long id) {
        return "/container/" + id;
    }

    @Override
    protected void addToOverview(Long id, AbstractPanel parentOrOverview, Integer activeTabIndex) {
        flowBean.addContainerToOverview(id, parentOrOverview, activeTabIndex, false);
    }

    @Override
    protected ContainerDTO findNext() {
        return containerService.findNextByActionUnit(unit.getActionUnit(), unit);
    }

    @Override
    protected ContainerDTO findPrevious() {
        return containerService.findPreviousByActionUnit(unit.getActionUnit(), unit);
    }

    @Override
    protected void doToggleValidate() {
        // not yet supported for containers
    }

    @Override
    ContainerDTO findUnitById(Long id) {
        return containerService.findById(id);
    }

    @Override
    public List<MenuModel> getAllParentBreadcrumbModels() {
        MenuModel breadcrumbModel = new DefaultMenuModel();
        breadcrumbModel.getElements().add(createHomeItem());
        if (unit != null && unit.getActionUnit() != null) {
            ActionUnitDTO actionUnit = actionUnitService.findById(unit.getActionUnit().getId());
            breadcrumbModel.getElements().add(createUnitItem(actionUnit));
        }
        breadcrumbModel.getElements().add(createRootTypeItem());
        return List.of(breadcrumbModel);
    }

    @Override
    protected DefaultMenuItem createRootTypeItem() {
        String command;
        if (isRoot) {
            command = "#{navBean.redirectToBookmarked('/container')}";
        } else {
            command = "#{flowBean.addContainerListPanel()}";
        }

        return DefaultMenuItem.builder()
                .value("Containers")
                .command(command)
                .update("@this")
                .id("rootContainers")
                .icon("bi bi-box-seam")
                .onstart(PF_BUI_CONTENT_SHOW)
                .oncomplete(PF_BUI_CONTENT_HIDE)
                .process(THIS)
                .build();
    }

    @Override
    public void initForms(boolean forceInit) {
        String typeName = resolveTypeName();
        Long projectId = unit.getActionUnit() != null ? unit.getActionUnit().getId() : null;

        FormUiDto form = Container.DETAILS_FORM;
        if (projectId != null) {
            FormUiDto base = CustomFormComposer.withoutFields(form, inactiveSystemFieldBindings(projectId, typeName));
            form = CustomFormComposer.withAdditionalFields(base, "Champs additionnels", additionalFields(projectId, typeName));
        }
        detailsForm = CustomFormComposer.deepCopy(form);

        initFormContext(forceInit);
    }

    /**
     * The label of the Container's own type concept, i.e. the type name field configurations
     * are keyed on ({@link TableFieldConfigService#DEFAULT_TYPE} when the container has none).
     */
    private String resolveTypeName() {
        return unit.getType() != null
                ? labelService.findLabelOf(unit.getType(), langBean.getLanguageCode()).getLabel()
                : TableFieldConfigService.DEFAULT_TYPE;
    }

    private Set<String> inactiveSystemFieldBindings(Long projectId, String typeName) {
        TypeFieldsConfig fieldsConfig = tableFieldConfigService.getFieldsConfig(projectId, ConfigurableTable.CONTENANT, typeName);
        return fieldsConfig.getFields().stream()
                .filter(f -> !f.isActive())
                .map(TypeFieldFormConfig::getValueBinding)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<CustomColUiDto> additionalFields(Long projectId, String typeName) {
        return tableFieldConfigService.getActiveAdditionalFields(projectId, ConfigurableTable.CONTENANT, typeName).stream()
                .map(field -> new CustomColUiDto.Builder().field(field).build())
                .toList();
    }

    @Override
    protected String getFormScopePropertyName() {
        return "type";
    }

    @Override
    protected void setFormScopePropertyValue(ConceptDTO concept) {
        unit.setType(concept);
    }

    @Override
    public void visualise(RevisionWithInfo<ContainerDTO> history) {
        // deactivated
    }

    @Override
    public String getAutocompleteClass() {
        return "container-autocomplete";
    }

    @Override
    public boolean save(Boolean validated) {
        return formContext.save();
    }

    public static class Builder {

        private final ContainerPanel containerPanel;

        public Builder(ObjectProvider<ContainerPanel> containerPanelProvider) {
            this.containerPanel = containerPanelProvider.getObject();
        }

        public Builder id(Long id) {
            containerPanel.setUnitId(id);
            return this;
        }

        public Builder breadcrumb(PanelBreadcrumb breadcrumb) {
            containerPanel.setBreadcrumb(breadcrumb);
            return this;
        }

        public ContainerPanel build() {
            containerPanel.init();
            return containerPanel;
        }
    }

    @Override
    public String getTabView() {
        return "/panel/tabview/containerTabView.xhtml";
    }

    @Override
    public String getPrefixPanelIndex() {
        return "container-" + unitId;
    }

    @Override
    public String svgIcon() {
        return "/resources/img/svg/box-seam.svg";
    }

    @Override
    public String getPanelTypeClass() {
        return "container";
    }
}
