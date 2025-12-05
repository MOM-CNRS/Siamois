package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseRecordingUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.RecordingUnitLazyDataModel;
import fr.siamois.ui.table.RecordingUnitTableViewModel;
import fr.siamois.ui.table.TableColumn;
import fr.siamois.utils.MessageUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.primefaces.event.RowEditEvent;
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
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RecordingUnitListPanel extends AbstractListPanel<RecordingUnit> implements Serializable {

    private final transient RecordingUnitService recordingUnitService;
    private final transient FormService formService;
    private final transient SpatialUnitTreeService spatialUnitTreeService;
    private final transient SpatialUnitService spatialUnitService;

    // locals
    private String actionUnitListErrorMessage;
    private Concept bulkEditTypeValue;

    /**
     * Modèle de vue pour la table :
     * - encapsule le LazyDataModel "pur data"
     * - expose les colonnes
     * - gère les EntityFormContext par ligne
     * - expose les opérations dont le panel a besoin (selectedUnits, rowData, addRow...)
     */
    private RecordingUnitTableViewModel tableModel;

    public RecordingUnitListPanel(ApplicationContext context) {
        super("panel.title.allrecordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel list-panel",
                context);

        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.formService = context.getBean(FormService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.spatialUnitService = context.getBean(SpatialUnitService.class);
    }

    @Override
    protected long countUnitsByInstitution() {
        return recordingUnitService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    /**
     * Méthode appelée par AbstractListPanel pour initialiser le lazy model.
     * On crée le lazy "pur data" et on le donne au RecordingUnitTableViewModel.
     * Ensuite, cette classe n'utilise plus jamais le lazy directement.
     */
    @Override
    protected BaseLazyDataModel<RecordingUnit> createLazyDataModel() {
        BaseRecordingUnitLazyDataModel lazy =
                new RecordingUnitLazyDataModel(recordingUnitService, sessionSettingsBean, langBean);

        // construction de la vue de table autour du lazy
        tableModel = new RecordingUnitTableViewModel(
                lazy,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService
        );

        return lazy; // l'abstraite en a besoin, mais ce panel ne s'en sert plus ensuite
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }

    @Override
    public String displayHeader() {
        return "/panel/header/recordingUnitListPanelHeader.xhtml";
    }

    @Override
    protected String getBreadcrumbKey() {
        return "common.entity.recordingUnits";
    }

    @Override
    protected String getBreadcrumbIcon() {
        return "bi bi-pencil-square";
    }

    public List<Person> authorsAvailable() {
        return personService.findAllAuthorsOfActionUnitByInstitution(sessionSettingsBean.getSelectedInstitution());
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
     * Exposé pour la vue JSF, utilisé dans <p:columns value="#{panelModel.columns}">
     */
    public List<TableColumn> getColumns() {
        return tableModel != null ? tableModel.getColumns() : List.of();
    }

    /**
     * À toi de remplir la définition des colonnes.
     */
    protected void configureTableColumns() {
        Concept NAME_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4285848")
                .build();
        CustomFieldText NAME_FIELD = new CustomFieldText.Builder()
                .label("common.label.name")
                .isSystemField(true)
                .valueBinding("description")
                .concept(NAME_CONCEPT)
                .build();
         NAME_FIELD.setId(1L);
         tableModel.getTableDefinition().addColumn(
             TableColumn.builder()
                 .id("identifier")
                 .headerKey("common.label.name")
                 .field(NAME_FIELD)
                 .sortable(true)
                 .visible(true)
                 .required(true)
                 .build()
         );
    }

    @Override
    public String display() {
        return "/panel/recordingUnitListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/recording-unit";
    }

    public void handleRowEdit(RowEditEvent<RecordingUnit> event) {
        RecordingUnit toSave = event.getObject();

        // un jour : refléter le contexte si tu passes par EntityFormContext
        // var ctx = tableModel.getRowContext(toSave);
        // if (ctx != null) ctx.flushBackToEntity();

        try {
            recordingUnitService.save(toSave, toSave.getType(), List.of(), List.of(), List.of());
        } catch (FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", toSave.getFullIdentifier());
            return;
        }

        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.updated", toSave.getFullIdentifier());
    }



    public static class RecordingUnitListPanelBuilder {

        private final RecordingUnitListPanel recordingUnitListPanel;

        public RecordingUnitListPanelBuilder(ObjectProvider<RecordingUnitListPanel> actionUnitListPanelProvider) {
            this.recordingUnitListPanel = actionUnitListPanelProvider.getObject();
        }

        public RecordingUnitListPanel.RecordingUnitListPanelBuilder breadcrumb(PanelBreadcrumb breadcrumb) {
            recordingUnitListPanel.setBreadcrumb(breadcrumb);
            return this;
        }

        public RecordingUnitListPanel build() {
            recordingUnitListPanel.init();
            return recordingUnitListPanel;
        }
    }
}
