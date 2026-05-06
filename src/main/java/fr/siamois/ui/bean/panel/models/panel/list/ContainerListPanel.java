package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.SpatialUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.mapper.ActionUnitMapper;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.lazydatamodel.ActionUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.ContainerLazyDataModel;
import fr.siamois.ui.lazydatamodel.scope.ActionUnitScope;
import fr.siamois.ui.lazydatamodel.tree.ActionUnitTreeTableLazyModel;
import fr.siamois.ui.table.ActionUnitTableViewModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.ActionUnitTableDefinitionFactory;
import fr.siamois.ui.table.definitions.ContainerTableDefinitionFactory;
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

import static fr.siamois.ui.lazydatamodel.scope.ActionUnitScope.Type.INSTITUTION;


@Slf4j
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ContainerListPanel extends AbstractListPanel<ContainerDTO> implements Serializable {

    // deps
    private final transient FormService formService;
    private final transient SpatialUnitTreeService spatialUnitTreeService;
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean<ActionUnitDTO> genericNewUnitDialogBean;
    private final transient SpatialUnitWriteVerifier spatialUnitWriteVerifier;
    private final transient NavBean navBean;
    private final transient InstitutionService institutionService;
    private final transient FormContextServices formContextServices;
    private final transient ActionUnitMapper actionUnitMapper;
    private final ContainerService containerService;

    // locals
    private String actionUnitListErrorMessage;


    public String getPrefixPanelIndex() {
        return "container-list";
    }

    @Override
    public String svgIcon() {
        return "/resources/img/svg/box-seam.svg";
    }

    @Override
    protected long countUnitsByInstitution() {
        // todo
        return 0;
    }

    @Override
    protected BaseLazyDataModel<ContainerDTO> createLazyDataModel() {
        ContainerLazyDataModel lazy =  new ContainerLazyDataModel(containerService, sessionSettingsBean);

        // construction de la vue de table autour du lazy
        tableModel = new ActionUnitTableViewModel(
                lazy,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                genericNewUnitDialogBean,
                null,
                institutionService,
                formContextServices,
                actionUnitService,
                actionUnitMapper
        );
        tableModel.setParentPanel(this);
        return lazy;
    }

    @Override
    public void setErrorMessage(String msg) {
        this.actionUnitListErrorMessage = msg;
    }


    public ContainerListPanel(ApplicationContext context, ActionUnitMapper actionUnitMapper, ContainerService containerService) {
        super("panel.title.allcontainers",
                "bi bi-box-seam",
                "siamois-panel container-panel list-panel",
                context);
        this.formService = context.getBean(FormService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.spatialUnitWriteVerifier = context.getBean(SpatialUnitWriteVerifier.class);
        this.navBean = context.getBean(NavBean.class);
        this.institutionService = context.getBean(InstitutionService.class);
        this.formContextServices = context.getBean(FormContextServices.class);
        this.actionUnitMapper = actionUnitMapper;
        this.containerService = containerService;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/containerListPanelHeader.xhtml";
    }


    @Override
    protected String getBreadcrumbKey() {
        return "common.entity.allcontainers";
    }

    @Override
    protected String getBreadcrumbIcon() {
        return "bi bi-box-seam";
    }



    public List<Person> authorsAvailable() {
        return personService.findAllAuthorsOfActionUnitByInstitution(sessionSettingsBean.getSelectedInstitution());
    }



    @Override
    public String display() {
        return "/panel/containerListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/container";
    }


    @Override
    void configureTableColumns() {
        ContainerTableDefinitionFactory.applyTo(tableModel);

        // configuration du bouton creer
        tableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.CONTAINER)
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
        return "container";
    }

    public static class ContainerListPanelBuilder {

        private final ContainerListPanel containerListPanel;

        public ContainerListPanelBuilder(ObjectProvider<ContainerListPanel> containerListPanelObjectProvider) {
            this.containerListPanel = containerListPanelObjectProvider.getObject();
        }

        public ContainerListPanel.ContainerListPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            containerListPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public ContainerListPanel build() {
            containerListPanel.init();
            return containerListPanel;
        }
    }







}
