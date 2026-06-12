package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.PhaseLazyDataModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.PhaseTableDefinitionFactory;
import fr.siamois.ui.table.viewmodel.PhaseTableViewModel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.List;

@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PhaseListPanel extends AbstractListPanel<PhaseDTO> implements Serializable {

    private final transient FormService formService;
    private final transient SpatialUnitTreeService spatialUnitTreeService;
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean<PhaseDTO> genericNewUnitDialogBean;
    private final transient NavBean navBean;
    private final transient InstitutionService institutionService;
    private final transient FormContextServices formContextServices;
    private final transient PhaseService phaseService;

    @Override
    public String getPrefixPanelIndex() {
        return "phase-list";
    }

    @Override
    public String svgIcon() {
        return "/resources/img/svg/layers.svg";
    }

    @Override
    protected long countUnitsByInstitution() {
        return 0;
    }

    @Override
    protected BaseLazyDataModel<PhaseDTO> createLazyDataModel() {
        PhaseLazyDataModel lazy = new PhaseLazyDataModel(phaseService, sessionSettingsBean);

        tableModel = new PhaseTableViewModel(
                lazy,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                genericNewUnitDialogBean,
                institutionService,
                formContextServices
        );
        tableModel.setParentPanel(this);
        return lazy;
    }

    @Override
    public void setErrorMessage(String msg) {
        // no-op
    }

    public PhaseListPanel(ApplicationContext context, PhaseService phaseService) {
        super("panel.title.allphases",
                "bi bi-layers",
                "siamois-panel phase-panel list-panel",
                context);
        this.formService = context.getBean(FormService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.navBean = context.getBean(NavBean.class);
        this.institutionService = context.getBean(InstitutionService.class);
        this.formContextServices = context.getBean(FormContextServices.class);
        this.phaseService = phaseService;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/phaseListPanelHeader.xhtml";
    }

    @Override
    protected String getBreadcrumbKey() {
        return "common.entity.allphases";
    }

    @Override
    protected String getBreadcrumbIcon() {
        return "bi bi-layers";
    }

    @Override
    protected String getTableClientIdPrefix() {
        return "phaseListForm:phaseList";
    }

    public List<Person> authorsAvailable() {
        return personService.findAllAuthorsOfActionUnitByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    public String display() {
        return "/panel/phaseListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/phase";
    }

    @Override
    void configureTableColumns() {
        PhaseTableDefinitionFactory.applyTo(tableModel);

        tableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.PHASE)
                        .scopeSupplier(NewUnitContext.Scope::none)
                        .insertPolicySupplier(() -> NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.ROOT)
                                .build())
                        .build()
        );
    }

    @Override
    public String getPanelTypeClass() {
        return "phase";
    }

    public static class PhaseListPanelBuilder {

        private final PhaseListPanel phaseListPanel;

        public PhaseListPanelBuilder(ObjectProvider<PhaseListPanel> provider) {
            this.phaseListPanel = provider.getObject();
        }

        public PhaseListPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            phaseListPanel.setBreadcrumb(breadcrumb);
            return this;
        }

        public PhaseListPanel build() {
            phaseListPanel.init();
            return phaseListPanel;
        }
    }
}
