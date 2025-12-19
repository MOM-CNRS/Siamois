package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.authorization.writeverifier.ActionUnitWriteVerifier;
import fr.siamois.domain.services.authorization.writeverifier.RecordingUnitWriteVerifier;
import fr.siamois.domain.services.authorization.writeverifier.SpatialUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.dialog.newunit.NewUnitContext;
import fr.siamois.ui.bean.dialog.newunit.UnitKind;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.*;
import fr.siamois.ui.lazydatamodel.tree.RecordingUnitTreeTableLazyModel;
import fr.siamois.ui.lazydatamodel.tree.SpatialUnitTreeTableLazyModel;
import fr.siamois.ui.table.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static fr.siamois.ui.bean.panel.models.panel.single.AbstractSingleEntity.SYSTEM_THESO;

@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Data
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpatialUnitListPanel extends AbstractListPanel<SpatialUnit>  implements Serializable {


    //deps
    private final transient FormService formService;
    private final transient SpatialUnitTreeService spatialUnitTreeService;
    private final transient SpatialUnitService spatialUnitService;
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean genericNewUnitDialogBean;
    private final transient SpatialUnitWriteVerifier spatialUnitWriteVerifier;
    private final transient NavBean navBean;
    private final transient InstitutionService institutionService;

    // locals
    private String spatialUnitListErrorMessage;

    public SpatialUnitListPanel(ApplicationContext context,
                                FormService formService,
                                SpatialUnitTreeService spatialUnitTreeService,
                                SpatialUnitService spatialUnitService,
                                FlowBean flowBean, GenericNewUnitDialogBean genericNewUnitDialogBean,
                                SpatialUnitWriteVerifier spatialUnitWriteVerifier, NavBean navBean, ActionUnitWriteVerifier actionUnitWriteVerifier, InstitutionService institutionService) {
        super("panel.title.allspatialunit",
                "bi bi-geo-alt",
                "siamois-panel spatial-unit-panel list-panel",
                context);
        this.formService = formService;
        this.spatialUnitTreeService = spatialUnitTreeService;
        this.spatialUnitService = spatialUnitService;
        this.flowBean = flowBean;
        this.genericNewUnitDialogBean = genericNewUnitDialogBean;
        this.spatialUnitWriteVerifier = spatialUnitWriteVerifier;
        this.navBean = navBean;
        this.institutionService = institutionService;
    }

    @Override
    protected String getBreadcrumbKey() {
        return "common.entity.spatialUnits";
    }

    @Override
    protected String getBreadcrumbIcon() {
        return "bi bi-geo-alt";
    }


    @Override
    public String displayHeader() {
        return "/panel/header/spatialUnitListPanelHeader.xhtml";
    }


    @Override
    protected long countUnitsByInstitution() {
        return spatialUnitService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }


    @Override
    protected BaseLazyDataModel<SpatialUnit> createLazyDataModel() {
        SpatialUnitLazyDataModel lazy = new SpatialUnitLazyDataModel(spatialUnitService, sessionSettingsBean, langBean);
        SpatialUnitTreeTableLazyModel lazyTree = new SpatialUnitTreeTableLazyModel(spatialUnitService, sessionSettingsBean.getSelectedInstitution().getId());

        // construction de la vue de table autour du lazy
        tableModel = new SpatialUnitTableViewModel(
                lazy,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                genericNewUnitDialogBean,
                spatialUnitWriteVerifier,
                lazyTree,
                institutionService
        );

        return lazy; // l'abstraite en a besoin, mais ce panel ne s'en sert plus ensuite
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.spatialUnitListErrorMessage = msg;
    }

    public List<ConceptLabel> categoriesAvailable() {
        List<Concept> cList = conceptService.findAllBySpatialUnitOfInstitution(sessionSettingsBean.getSelectedInstitution());

        return new ArrayList<>(cList.stream()
                .map(concept -> labelService.findLabelOf(
                        concept, langBean.getLanguageCode()
                ))
                .toList());

    }

    public List<Person> authorsAvailable() {
        return personService.findAllAuthorsOfSpatialUnitByInstitution(sessionSettingsBean.getSelectedInstitution());
    }



    @Override
    public String display() {
        return "/panel/spatialUnitListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/spatial-unit";
    }

    public static class SpatialUnitListPanelBuilder {

        private final SpatialUnitListPanel spatialUnitListPanel;

        public SpatialUnitListPanelBuilder(ObjectProvider<SpatialUnitListPanel> spatialUnitListPanelProvider) {
            this.spatialUnitListPanel = spatialUnitListPanelProvider.getObject();
        }

        public SpatialUnitListPanel.SpatialUnitListPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            spatialUnitListPanel.setBreadcrumb(breadcrumb);

            return this;
        }

        public SpatialUnitListPanel build() {
            spatialUnitListPanel.init();
            return spatialUnitListPanel;
        }
    }

    @Override
    public void init() {
        // super.init() va appeler createLazyDataModel() → tableModel est initialisé ici
        super.init();

        // initialiser la sélection via l'API du tableModel (pas accès direct au lazy)
        tableModel.getLazyDataModel().setSelectedUnits(new ArrayList<>());

        // Configurer les colonnes de la table
        configureTableColumns();
    }

    /**
     * À toi de remplir la définition des colonnes.
     */
    protected void configureTableColumns() {
        tableModel.getTableDefinition().addColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.spatialunit.column.name")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(true)
                        .sortField("name")

                        // What to display inside <h:outputText>
                        .valueKey("name")

                        // What to do on click (Pattern A key)
                        .action(TableColumnAction.GO_TO_SPATIAL_UNIT)

                        // CommandLink behavior
                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("parents")
                        .headerKey("table.spatialunit.column.parents")
                        .headerIcon("bi bi-pencil-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("parents")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("spatialUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("children")
                        .headerKey("table.spatialunit.column.children")
                        .headerIcon("bi bi-pencil-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("children")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("spatialUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("action")
                        .headerKey("table.spatialunit.column.actions")
                        .headerIcon("bi bi-arrow-down-square")
                        .visible(true)
                        .toggleable(true)

                        .countKey("actions")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("actionUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        // configuration du bouton creer
        tableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.SPATIAL)
                        .scopeSupplier(NewUnitContext.Scope::none) // ou linkedTo(...)
                        .insertPolicySupplier(() -> NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.ROOT)
                                .build())
                        .build()
        );
    }





}
