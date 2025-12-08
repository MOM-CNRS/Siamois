package fr.siamois.ui.table;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.form.FormService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitTreeService;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.lazydatamodel.BaseRecordingUnitLazyDataModel;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.Month;

/**
 * View model spécifique pour les tableaux de RecordingUnit.
 *
 * - spécialise EntityTableViewModel pour T = RecordingUnit, ID = Long
 * - implémente :
 *      - resolveRowFormFor
 *      - configureRowSystemFields
 */
@Getter
public class RecordingUnitTableViewModel extends EntityTableViewModel<RecordingUnit, Long> {

    /** Lazy model spécifique RecordingUnit (accès à selectedUnits, etc.) */
    private final BaseRecordingUnitLazyDataModel recordingUnitLazyDataModel;

    private final SessionSettingsBean sessionSettingsBean;

    public RecordingUnitTableViewModel(BaseRecordingUnitLazyDataModel lazyDataModel,
                                       FormService formService,
                                       SessionSettingsBean sessionSettingsBean,
                                       SpatialUnitTreeService spatialUnitTreeService,
                                       SpatialUnitService spatialUnitService) {

        super(
                lazyDataModel,
                formService,
                spatialUnitTreeService,
                spatialUnitService,
                RecordingUnit::getId,   // idExtractor
                "type"                  // formScopeValueBinding
        );
        this.recordingUnitLazyDataModel = lazyDataModel;
        this.sessionSettingsBean = sessionSettingsBean;
    }

    @Override
    protected CustomForm resolveRowFormFor(RecordingUnit ru) {
        Concept type = ru.getType();
        if (type == null) {
            return null;
        }
        return formService.findCustomFormByRecordingUnitTypeAndInstitutionId(
                type,
                sessionSettingsBean.getSelectedInstitution()
        );
    }

    @Override
    protected void configureRowSystemFields(RecordingUnit ru, CustomForm rowForm) {
        if (rowForm == null || rowForm.getLayout() == null) {
            return;
        }

        for (CustomField field : getAllFieldsFromForm(rowForm)) {

            // Recording unit identifier
            if ("identifier".equals(field.getValueBinding()) && field instanceof CustomFieldInteger cfi) {
                if (ru.getActionUnit() != null) {
                    cfi.setMaxValue(ru.getActionUnit().getMaxRecordingUnitCode());
                    cfi.setMinValue(ru.getActionUnit().getMinRecordingUnitCode());
                }
            }

            // Min and max datetime
            if (field instanceof CustomFieldDateTime dt) {
                if ("openingDate".equals(field.getValueBinding()) && ru.getClosingDate() != null) {
                    dt.setMax(ru.getClosingDate().toLocalDateTime());
                    dt.setMin(LocalDateTime.of(1000, Month.JANUARY, 1, 1, 1));
                }
                if ("closingDate".equals(field.getValueBinding()) && ru.getOpeningDate() != null) {
                    dt.setMin(ru.getOpeningDate().toLocalDateTime());
                    dt.setMax(LocalDateTime.of(9999, Month.DECEMBER, 31, 23, 59));
                }
            }
        }
    }
}
