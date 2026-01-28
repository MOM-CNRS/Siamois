package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
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
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.SpatialUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.scope.SpatialUnitScope;
import fr.siamois.ui.lazydatamodel.tree.SpatialUnitTreeTableLazyModel;
import fr.siamois.ui.table.SpatialUnitTableViewModel;
import fr.siamois.ui.table.ToolbarCreateConfig;
import fr.siamois.ui.table.definitions.SpatialUnitTableDefinitionFactory;
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

import static fr.siamois.ui.lazydatamodel.scope.SpatialUnitScope.Type.INSTITUTION;

@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Data
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SpatialUnitListPanel extends AbstractListPanel<SpatialUnit>  implements Serializable {


    //deps
    private final transient FormService formService;
    private final transient SpatialUnitTreeService spatialUnitTreeService;
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean<SpatialUnit> genericNewUnitDialogBean;
    private final transient SpatialUnitWriteVerifier spatialUnitWriteVerifier;
    private final transient NavBean navBean;
    private final transient InstitutionService institutionService;
    private final transient FormContextServices formContextServices;

    // locals
    private String spatialUnitListErrorMessage;

    public SpatialUnitListPanel(ApplicationContext context) {
        super("panel.title.allspatialunit",
                "bi bi-geo-alt",
                "siamois-panel spatial-unit-panel list-panel",
                context);
        this.formService = context.getBean(FormService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.spatialUnitWriteVerifier = context.getBean(SpatialUnitWriteVerifier.class);
        this.navBean = context.getBean(NavBean.class);
        this.institutionService = context.getBean(InstitutionService.class);
        this.formContextServices = context.getBean(FormContextServices.class);
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
        SpatialUnitTreeTableLazyModel lazyTree = new SpatialUnitTreeTableLazyModel(spatialUnitService,
                SpatialUnitScope.builder()
                        .institutionId(sessionSettingsBean.getSelectedInstitution().getId())
                        .type(INSTITUTION)
                        .build());

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
                institutionService,
                formContextServices
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

    }

    @Override
    void configureTableColumns() {
        SpatialUnitTableDefinitionFactory.applyTo(tableModel);

        // configuration du bouton creer
        tableModel.setToolbarCreateConfig(
                ToolbarCreateConfig.builder()
                        .kindToCreate(UnitKind.SPATIAL)
                        .scopeSupplier(NewUnitContext.Scope::none)
                        .insertPolicySupplier(() -> NewUnitContext.UiInsertPolicy.builder()
                                .listInsert(NewUnitContext.ListInsert.TOP)
                                .treeInsert(NewUnitContext.TreeInsert.ROOT)
                                .build())
                        .build()
        );
    }





}
