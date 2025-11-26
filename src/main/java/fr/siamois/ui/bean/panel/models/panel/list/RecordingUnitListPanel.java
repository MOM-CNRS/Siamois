package fr.siamois.ui.bean.panel.models.panel.list;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerText;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.RecordingUnitLazyDataModel;
import fr.siamois.utils.MessageUtils;
import lombok.Data;
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
public class RecordingUnitListPanel extends AbstractListPanel<RecordingUnit>  implements Serializable {

    @Data
    public class ColumnModel {
        private String property;
        private boolean sortable;
        private boolean isColumnEnabled = true;
        private int width;
        private boolean required = true;
        private boolean readOnly = false;
        private String name = "name";
        private CustomField field;

    }

    private final transient RecordingUnitService recordingUnitService;

    // locals
    private String actionUnitListErrorMessage;
    private Concept bulkEditTypeValue;
    private List<ColumnModel> columns = new ArrayList<>();

    // temp to remove todo
    private CustomFormResponse formResponse;
    CustomFieldText customFieldText = new CustomFieldText();
    private CustomFieldAnswerText customFieldAnswer;


    @Override
    protected long countUnitsByInstitution() {
        return recordingUnitService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    protected BaseLazyDataModel<RecordingUnit> createLazyDataModel() {
        return new RecordingUnitLazyDataModel(recordingUnitService, sessionSettingsBean, langBean);
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }

    // todo : delete
    public boolean isColumnEnabled(CustomField field) { return true;}




    public RecordingUnitListPanel(ApplicationContext context) {
        super("panel.title.allrecordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel list-panel",
                context);
        recordingUnitService = context.getBean(RecordingUnitService.class);
        ColumnModel columnModel = new ColumnModel();
        columnModel.sortable = true;
        columnModel.property = "identifier";
        columnModel.width = 100;
        columnModel.field = customFieldText;
        columns.add(columnModel);
        formResponse = new CustomFormResponse();
        customFieldAnswer = new CustomFieldAnswerText();
        customFieldAnswer.setValue("Test");
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
        super.init();
        lazyDataModel.setSelectedUnits(new ArrayList<>());
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

        try {
            recordingUnitService.save(toSave, toSave.getType(), List.of(),  List.of(),  List.of());
        }
        catch(FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", toSave.getFullIdentifier());
            return ;
        }

        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.updated", toSave.getFullIdentifier());
    }

    public void saveFieldBulk() {
        List<Long> ids = lazyDataModel.getSelectedUnits().stream()
                .map(RecordingUnit::getId)
                .toList();
        int updateCount = recordingUnitService.bulkUpdateType(ids, bulkEditTypeValue);
        // Update in-memory list (for UI sync)
        for (RecordingUnit ru : lazyDataModel.getSelectedUnits()) {
            ru.setType(bulkEditTypeValue);
        }
        MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.bulkUpdated", updateCount);
    }

    public void duplicateRow() {
        // Create a copy from selected row
        RecordingUnit original = lazyDataModel.getRowData();
        RecordingUnit newRec = new RecordingUnit(original);
        newRec.setIdentifier(recordingUnitService.generateNextIdentifier(newRec));

        // Save it
        newRec = recordingUnitService.save(newRec, newRec.getType(), List.of(), List.of(), List.of());

        // Add it to the model
        lazyDataModel.addRowToModel(newRec);
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
