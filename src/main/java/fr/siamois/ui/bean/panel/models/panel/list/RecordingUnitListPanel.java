package fr.siamois.ui.bean.panel.models.panel.list;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.authorization.writeverifier.RecordingUnitWriteVerifier;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.dialog.newunit.GenericNewUnitDialogBean;
import fr.siamois.ui.bean.panel.FlowBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.form.FormContextServices;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.BaseRecordingUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.RecordingUnitLazyDataModel;
import fr.siamois.ui.lazydatamodel.scope.RecordingUnitScope;
import fr.siamois.ui.lazydatamodel.tree.RecordingUnitTreeTableLazyModel;
import fr.siamois.ui.table.RecordingUnitTableViewModel;
import fr.siamois.ui.table.definitions.RecordingUnitTableDefinitionFactory;
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

@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@Getter
@Setter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RecordingUnitListPanel extends AbstractListPanel<RecordingUnit> implements Serializable {

    private final transient RecordingUnitService recordingUnitService;
    private final transient FormService formService;
    private final transient SpatialUnitTreeService spatialUnitTreeService;
    private final transient FlowBean flowBean;
    private final transient GenericNewUnitDialogBean<RecordingUnit> genericNewUnitDialogBean;
    private final transient RecordingUnitWriteVerifier recordingUnitWriteVerifier;
    private final transient NavBean navBean;
    private final transient FormContextServices formContextServices;

    // locals
    private String actionUnitListErrorMessage;
    private Concept bulkEditTypeValue;

    public String getPanelIndex() {
        return "recording-unit-list";
    }

    @SuppressWarnings("unchecked")
    public RecordingUnitListPanel(ApplicationContext context) {
        super("panel.title.allrecordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel list-panel",
                context);

        this.recordingUnitService = context.getBean(RecordingUnitService.class);
        this.formService = context.getBean(FormService.class);
        this.spatialUnitTreeService = context.getBean(SpatialUnitTreeService.class);
        this.flowBean = context.getBean(FlowBean.class);
        this.genericNewUnitDialogBean = context.getBean(GenericNewUnitDialogBean.class);
        this.recordingUnitWriteVerifier = context.getBean(RecordingUnitWriteVerifier.class);
        this.navBean = context.getBean(NavBean.class);
        this.formContextServices = context.getBean(FormContextServices.class);
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
        RecordingUnitTreeTableLazyModel lazyTree = new RecordingUnitTreeTableLazyModel(recordingUnitService,
                RecordingUnitScope.builder()
                        .institutionId(sessionSettingsBean.getSelectedInstitution().getId())
                        .type(RecordingUnitScope.Type.RU_IN_INSTITUTION)
                        .build());

        // construction de la vue de table autour du lazy
        tableModel = new RecordingUnitTableViewModel(
                lazy,
                formService,
                sessionSettingsBean,
                spatialUnitTreeService,
                spatialUnitService,
                navBean,
                flowBean,
                genericNewUnitDialogBean,
                recordingUnitWriteVerifier,
                recordingUnitService,
                lazyTree,
                langBean,
                formContextServices
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
    }



    /**
     *Definition des colonnes de la table
     */
    protected void configureTableColumns() {

        RecordingUnitTableDefinitionFactory.applyTo(tableModel);

        // no toolbar button in institution context

    }

    @Override
    public String display() {
        return "/panel/recordingUnitListPanel.xhtml";
    }

    @Override
    public String ressourceUri() {
        return "/recording-unit";
    }

    public void handleRowEdit(RowEditEvent<RecordingUnitDTO> event) {
        handleRuRowEdit(event, recordingUnitService, langBean);
    }

    public static void handleRuRowEdit(RowEditEvent<RecordingUnitDTO> event, RecordingUnitService recordingUnitService, LangBean langBean) {
        RecordingUnitDTO toSave = event.getObject();

        if (recordingUnitService.fullIdentifierAlreadyExistInAction(toSave)) {
            MessageUtils.displayWarnMessage(langBean, "recordingunit.error.identifier.alreadyExists");
            return;
        }

        try {
            recordingUnitService.save(toSave, toSave.getType());
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

    @Override
    public String getPanelTypeClass() {
        return "recording-unit";
    }
}
