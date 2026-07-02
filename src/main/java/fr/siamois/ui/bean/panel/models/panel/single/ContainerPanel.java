package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.bean.RedirectBean;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.bean.panel.models.panel.AbstractPanel;
import fr.siamois.ui.form.dto.FormUiDto;
import fr.siamois.ui.mapper.FormMapper;
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

@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ContainerPanel extends AbstractSingleEntityPanel<ContainerDTO> implements Serializable {

    private final transient FormMapper formMapper;
    private final transient ContainerService containerService;
    private final transient RedirectBean redirectBean;

    @Override
    protected boolean documentExistsInUnitByHash(ContainerDTO unit, String hash) {
        return false;
    }

    @Override
    protected void addDocumentToUnit(Document doc, ContainerDTO unit) {
        // not yet supported for containers
    }

    protected ContainerPanel(FormMapper formMapper, ApplicationContext context) {
        super("common.entity.container",
                "bi bi-box-seam",
                "siamois-panel container-panel single-panel",
                context);
        this.formMapper = formMapper;
        this.containerService = context.getBean(ContainerService.class);
        this.redirectBean = context.getBean(RedirectBean.class);
    }

    public String entityRessourceUri() {
        return "/container/" + unitId;
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
    public void init() {
        try {
            detailsForm = formContextServices.getConversionService().convert(Container.DETAILS_FORM, FormUiDto.class);
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
        flowBean.addContainerToOverview(id, parentOrOverview, activeTabIndex);
    }

    @Override
    protected Long findNextId() {
        return unit.getId();
    }

    @Override
    protected Long findPreviousId() {
        return unit.getId();
    }

    @Override
    public boolean hasPreviousNext() {
        return false;
    }

    @Override
    public void toggleValidate() {
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
        initFormContext(forceInit);
    }

    @Override
    protected String getFormScopePropertyName() {
        return "";
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
