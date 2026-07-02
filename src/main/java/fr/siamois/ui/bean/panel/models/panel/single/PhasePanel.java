package fr.siamois.ui.bean.panel.models.panel.single;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.history.RevisionWithInfo;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
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
public class PhasePanel extends AbstractSingleEntityPanel<PhaseDTO> implements Serializable {

    private final transient FormMapper formMapper;
    private final transient PhaseService phaseService;
    private final transient RedirectBean redirectBean;

    @Override
    protected boolean documentExistsInUnitByHash(PhaseDTO unit, String hash) {
        return false;
    }

    @Override
    protected void addDocumentToUnit(Document doc, PhaseDTO unit) {
        // not yet supported
    }

    protected PhasePanel(FormMapper formMapper, ApplicationContext context) {
        super("common.entity.phase",
                "bi bi-layers",
                "siamois-panel phase-panel single-panel",
                context);
        this.formMapper = formMapper;
        this.phaseService = context.getBean(PhaseService.class);
        this.redirectBean = context.getBean(RedirectBean.class);
    }

    public String entityRessourceUri() {
        return "/phase/" + unitId;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/phasePanelHeader.xhtml";
    }

    @Override
    public UnitKind getCreationUnitKind() {
        return UnitKind.PHASE;
    }

    @Override
    public void refreshUnit() {
        errorMessage = null;
        unit = null;

        try {
            unit = phaseService.findById(unitId);
            this.titleCodeOrTitle = unit.getIdentifier();
            initForms(true);
        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load phase: " + e.getMessage();
        }

        documents = List.of();
    }

    @Override
    public void init() {
        try {
            detailsForm = formContextServices.getConversionService().convert(Phase.DETAILS_FORM, FormUiDto.class);
            activeTabIndex = 0;

            if (unitId == null) {
                this.errorMessage = "The ID of the phase must be defined";
                return;
            }

            refreshUnit();

            if (this.unit == null) {
                log.error("Phase page accessed without valid ID");
                errorMessage = "Phase page accessed without valid ID";
            }
        } catch (RuntimeException e) {
            this.errorMessage = "Failed to load phase: " + e.getMessage();
            redirectBean.redirectTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<PersonDTO> authorsAvailable() {
        return List.of();
    }

    @Override
    protected String getFocusPath(Long id) {
        return "/phase/" + id;
    }

    @Override
    protected void addToOverview(Long id, AbstractPanel parentOrOverview, Integer activeTabIndex) {
        flowBean.addPhaseToOverview(id, parentOrOverview, activeTabIndex);
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
        // not yet supported
    }

    @Override
    PhaseDTO findUnitById(Long id) {
        return phaseService.findById(id);
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
        String command = isRoot
                ? "#{navBean.redirectToBookmarked('/phase')}"
                : "#{flowBean.addPhaseListPanel()}";

        return DefaultMenuItem.builder()
                .value("Phases")
                .command(command)
                .update("@this")
                .id("rootPhases")
                .icon("bi bi-layers")
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
    public void visualise(RevisionWithInfo<PhaseDTO> history) {
        // deactivated
    }

    @Override
    public String getAutocompleteClass() {
        return "phase-autocomplete";
    }

    @Override
    public boolean save(Boolean validated) {
        return formContext.save();
    }

    public static class Builder {

        private final PhasePanel phasePanel;

        public Builder(ObjectProvider<PhasePanel> provider) {
            this.phasePanel = provider.getObject();
        }

        public Builder id(Long id) {
            phasePanel.setUnitId(id);
            return this;
        }

        public Builder breadcrumb(PanelBreadcrumb breadcrumb) {
            phasePanel.setBreadcrumb(breadcrumb);
            return this;
        }

        public PhasePanel build() {
            phasePanel.init();
            return phasePanel;
        }
    }

    @Override
    public String getTabView() {
        return "/panel/tabview/phaseTabView.xhtml";
    }

    @Override
    public String getPrefixPanelIndex() {
        return "phase-" + unitId;
    }

    @Override
    public String svgIcon() {
        return "/resources/img/svg/layers.svg";
    }

    @Override
    public String getPanelTypeClass() {
        return "phase";
    }
}
