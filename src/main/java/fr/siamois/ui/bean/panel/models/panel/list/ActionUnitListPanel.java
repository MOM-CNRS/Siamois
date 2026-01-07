package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.SpatialUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.ActionUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.scope.ActionUnitScope;
import fr.siamois.ui.lazydatamodel.tree.ActionUnitTreeTableLazyModel;
import fr.siamois.ui.table.ActionUnitTableViewModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.ActionUnitTableDefinitionFactory;
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
public class ActionUnitListPanel extends AbstractListPanel<ActionUnit> implements Serializable {

    // deps
    private final transient FormService formService;
    private final transient SpatialUnitTreeService spatialUnitTreeService;
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean<ActionUnit> genericNewUnitDialogBean;
    private final transient SpatialUnitWriteVerifier spatialUnitWriteVerifier;
    private final transient NavBean navBean;
    private final transient InstitutionService institutionService;

    // locals
    private String actionUnitListErrorMessage;


    @Override
    protected long countUnitsByInstitution() {
        return actionUnitService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    protected BaseLazyDataModel<ActionUnit> createLazyDataModel() {
        ActionUnitLazyDataModel lazy =  new ActionUnitLazyDataModel(actionUnitService, sessionSettingsBean, langBean);
        ActionUnitTreeTableLazyModel lazyTree = new ActionUnitTreeTableLazyModel(actionUnitService,
                ActionUnitScope.builder()
                        .institutionId(sessionSettingsBean.getSelectedInstitution().getId())
                        .type(INSTITUTION)
                        .build());

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
                lazyTree,
                institutionService
        );
        return lazy;
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.actionUnitListErrorMessage = msg;
    }


    public ActionUnitListPanel(ApplicationContext context) {
        super("panel.title.allactionunit",
                "bi bi-arrow-down-square",
                "siamois-panel action-unit-panel list-panel",
                context);
        this.formService = context.getBean(FormService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.spatialUnitWriteVerifier = context.getBean(SpatialUnitWriteVerifier.class);
        this.navBean = context.getBean(NavBean.class);
        this.institutionService = context.getBean(InstitutionService.class);
    }

    @Override
    public String displayHeader() {
        return "/panel/header/actionUnitListPanelHeader.xhtml";
    }


    @Override
    protected String getBreadcrumbKey() {
        return "common.entity.actionUnits";
    }

    @Override
    protected String getBreadcrumbIcon() {
        return "bi bi-arrow-down-square";
    }



    public List<Person> authorsAvailable() {
        return personService.findAllAuthorsOfActionUnitByInstitution(sessionSettingsBean.getSelectedInstitution());
    }



    @Override
    public String display() {
        return "/panel/actionUnitListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/action-unit";
    }

    public static class ActionUnitListPanelBuilder {

        private final ActionUnitListPanel actionUnitListPanel;

        public ActionUnitListPanelBuilder(ObjectProvider<ActionUnitListPanel> actionUnitListPanelProvider) {
            this.actionUnitListPanel = actionUnitListPanelProvider.getObject();
        }

        public ActionUnitListPanel.ActionUnitListPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            actionUnitListPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public ActionUnitListPanel build() {
            actionUnitListPanel.init();
            return actionUnitListPanel;
        }
    }

    @Override
    void configureTableColumns() {
        ActionUnitTableDefinitionFactory.applyTo(tableModel);

        // configuration du bouton creer
        tableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.ACTION)
                        .scopeSupplier(NewUnitContext.Scope::none)
                        .insertPolicySupplier(() -> NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.ROOT)
                                .build())
                        .build()
        );
    }







}
