package fr.siamois.ui.bean.panel.models.panel.list;


import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.person.PersonService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.domain.services.vocabulary.FieldConfigurationService;
import fr.siamois.domain.services.vocabulary.FieldService;
import fr.siamois.domain.services.vocabulary.LabelService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.NavBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.bean.panel.models.PanelBreadcrumb;
import fr.siamois.ui.lazydatamodel.BaseLazyDataModel;
import fr.siamois.ui.lazydatamodel.RecordingUnitLazyDataModel;
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
public class RecordingUnitListPanel extends AbstractListPanel<RecordingUnit>  implements Serializable {

    private final transient RecordingUnitService recordingUnitService;

    // locals
    private String actionUnitListErrorMessage;
    private Concept bulkEditTypeValue;


    @Override
    protected long countUnitsByInstitution() {
        return actionUnitService.countByInstitution(sessionSettingsBean.getSelectedInstitution());
    }

    @Override
    protected BaseLazyDataModel<RecordingUnit> createLazyDataModel() {
        return new RecordingUnitLazyDataModel(recordingUnitService, sessionSettingsBean, langBean);
    }

    @Override
    protected void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }




    public RecordingUnitListPanel(ApplicationContext context) {
        super("panel.title.allrecordingunit",
                "bi bi-pencil-square",
                "siamois-panel recording-unit-panel list-panel",
                context);
        recordingUnitService = context.getBean(RecordingUnitService.class);
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
        return "/recordingunit";
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
