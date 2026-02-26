package fr.siamois.ui.form.savestrategy;

import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.EntityFormContextSaveStrategy;
import fr.siamois.utils.MessageUtils;

public class RecordingUnitSaveStrategy implements EntityFormContextSaveStrategy<RecordingUnitDTO> {
    @Override
    public boolean save(EntityFormContext<RecordingUnitDTO> context) {

        context.flushBackToEntity();
        // Custom save logic for RecordingUnit
        RecordingUnitDTO unit = context.getUnit();
        RecordingUnitService service = context.getRecordingUnitService();
        LangBean langBean = context.getLangBean();

        if (service.fullIdentifierAlreadyExistInAction(unit)) {
            MessageUtils.displayWarnMessage(langBean, "recording.error.identifier.alreadyExists");
            return false;
        }

        try {
            service.save(unit, unit.getType());
        } catch (FailedRecordingUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", unit);
            return false;
        }

        return true;

    }
}
