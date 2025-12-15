package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.authorization.writeverifier.RecordingUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseRecordingUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.RecordingUnitLazyDataModel;
import fr.siamois.ui.table.*;
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
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean genericNewUnitDialogBean;
    private final transient RecordingUnitWriteVerifier recordingUnitWriteVerifier;

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

    public RecordingUnitListPanel(ApplicationContext context, GenericNewUnitDialogBean genericNewUnitDialogBean, RecordingUnitWriteVerifier recordingUnitWriteVerifier) {
        super("panel.title.allrecordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel list-panel",
                context);

        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.formService = context.getBean(FormService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.spatialUnitService = context.getBean(SpatialUnitService.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.recordingUnitWriteVerifier = context.getBean(RecordingUnitWriteVerifier.class);
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
                spatialUnitService,
                flowBean,
                genericNewUnitDialogBean,
                recordingUnitWriteVerifier
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
        Concept TYPE_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4287605")
                .build();
        Concept OPENINGDATE_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286198")
                .build();
        Concept AUTHOR_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286195")
                .build();
        Concept CONTRIBUTORS_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286195")
                .build();
        Concept ACTION_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286244")
                .build();
        Concept SPATIAL_CONCEPT = new Concept.Builder()
                .vocabulary(SYSTEM_THESO)
                .externalId("4286245")
                .build();
        CustomFieldSelectOneFromFieldCode TYPE_FIELD = new CustomFieldSelectOneFromFieldCode.Builder()
                .label("recordingunit.property.type")
                .isSystemField(true)
                .valueBinding("type")
                .concept(TYPE_CONCEPT)
                .fieldCode("SIARU.TYPE")
                .styleClass("mr-2 recording-unit-type-chip")
                .build();
        CustomFieldDateTime DATE_FIELD = new CustomFieldDateTime.Builder()
                .label("recordingunit.field.openingDate")
                .isSystemField(true)
                .valueBinding("openingDate")
                .concept(OPENINGDATE_CONCEPT)
                .showTime(false)
                .build();
        CustomFieldSelectOnePerson AUTHOR_FIELD = new CustomFieldSelectOnePerson.Builder()
                .label("recordingunit.field.author")
                .isSystemField(true)
                .valueBinding("author")
                .concept(AUTHOR_CONCEPT)
                .build();
        CustomFieldSelectMultiplePerson CONTRIBUTORS_FIELD = new CustomFieldSelectMultiplePerson.Builder()
                .label("recordingunit.field.contributors")
                .isSystemField(true)
                .valueBinding("contributors")
                .concept(CONTRIBUTORS_CONCEPT)
                .build();
        CustomFieldSelectOneActionUnit ACTION_FIELD = new CustomFieldSelectOneActionUnit.Builder()
                .label("recordingunit.field.actionUnit")
                .isSystemField(true)
                .valueBinding("actionUnit")
                .concept(ACTION_CONCEPT)
                .build();
        CustomFieldSelectOneSpatialUnit SPATIAL_FIELD = new CustomFieldSelectOneSpatialUnit.Builder()
                .label("recordingunit.field.spatialUnit")
                .isSystemField(true)
                .valueBinding("spatialUnit")
                .concept(SPATIAL_CONCEPT)
                .build();

        DATE_FIELD.setId(2L);
        TYPE_FIELD.setId(1L);
        AUTHOR_FIELD.setId(3L);
        CONTRIBUTORS_FIELD.setId(4L);
        ACTION_FIELD.setId(5L);
        SPATIAL_FIELD.setId(6L);
        tableModel.getTableDefinition().addColumn(
                CommandLinkColumn.builder()
                        .id("identifierCol")
                        .headerKey("table.recordingunit.column.identifier")
                        .visible(true)

                        // PrimeFaces metadata equivalents
                        .toggleable(false)
                        .sortable(true)
                        .sortField("full_identifier")

                        // What to display inside <h:outputText>
                        .valueKey("fullIdentifier")

                        // What to do on click (Pattern A key)
                        .action(TableColumnAction.GO_TO_RECORDING_UNIT)

                        // CommandLink behavior
                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );
         tableModel.getTableDefinition().addColumn(
                 FormFieldColumn.builder()
                 .id("type")
                 .headerKey("recordingunit.property.type")
                 .field(TYPE_FIELD)
                 .sortable(true)
                 .visible(true)
                 .required(true)
                 .build()
         );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("openingDate")
                        .headerKey("recordingunit.field.openingDate")
                        .field(DATE_FIELD)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("author")
                        .headerKey("recordingunit.field.author")
                        .field(AUTHOR_FIELD)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("contributors")
                        .headerKey("recordingunit.field.contributors")
                        .field(CONTRIBUTORS_FIELD)
                        .sortable(true)
                        .visible(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("action")
                        .headerKey("recordingunit.field.actionUnit")
                        .field(ACTION_FIELD)
                        .sortable(true)
                        .visible(true)
                        .readOnly(true)
                        .required(true)
                        .build()
        );
        tableModel.getTableDefinition().addColumn(
                FormFieldColumn.builder()
                        .id("spatial")
                        .headerKey("recordingunit.field.spatialUnit")
                        .field(SPATIAL_FIELD)
                        .sortable(true)
                        .visible(true)
                        .readOnly(false)
                        .required(true)
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
                        .addRenderedKey("recordingUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );

        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("childre,")
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
                        .addRenderedKey("recordingUnitCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
                        .build()
        );


        tableModel.getTableDefinition().addColumn(
                RelationColumn.builder()
                        .id("specimen")
                        .headerKey("common.entity.specimen")
                        .headerIcon("bi bi-bucket")
                        .visible(true)
                        .toggleable(true)

                        .countKey("specimenList")

                        .viewIcon("bi bi-eye")
                        .viewAction(TableColumnAction.VIEW_RELATION)
                        .viewTargetIndex(0)

                        .addEnabled(true)
                        .addIcon("bi bi-plus-square")
                        .addAction(TableColumnAction.ADD_RELATION)
                        .addRenderedKey("specimenCreateAllowed")

                        .processExpr("@this")
                        .updateExpr("flow")
                        .onstartJs("PF('buiContent').show()")
                        .oncompleteJs("PF('buiContent').hide();handleScrollToTop();")
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

        // todo : refléter le contexte si tu passes par EntityFormContext
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
