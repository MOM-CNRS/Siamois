package fr.siamois.ui.form;

import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;

public class RecordingUnitSaveStrategy implements EntityFormContextSaveStrategy<RecordingUnit> {
    @Override
    public boolean save(EntityFormContext<RecordingUnit> context) {

        context.flushBackToEntity();
        // Custom save logic for RecordingUnit
        RecordingUnit unit = context.getUnit();
        RecordingUnitService service = context.getRecordingUnitService();
        LangBean langBean = context.getLangBean();

        if (service.fullIdentifierAlreadyExistInAction(unit)) {
            MessageUtils.displayWarnMessage(langBean, "recording.error.identifier.alreadyExists");
            return false;
        }

        try {
            service.save(unit, unit.getType());
        } catch (FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", unit.getFullIdentifier());
            return false;
        }

        return true;

    }
}
