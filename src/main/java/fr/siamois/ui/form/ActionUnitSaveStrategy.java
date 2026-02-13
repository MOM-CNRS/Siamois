package fr.siamois.ui.form;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;

public class ActionUnitSaveStrategy implements EntityFormContextSaveStrategy<ActionUnit> {
    @Override
    public boolean save(EntityFormContext<ActionUnit> context) {

        context.flushBackToEntity();
        // Custom save logic for Action Unit
        ActionUnit unit = context.getUnit();
        ActionUnitService service = context.getActionUnitService();
        LangBean langBean = context.getLangBean();

        try {
            service.save(unit);
        } catch (FailedActionUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", unit.getFullIdentifier());
            return false;
        }

        return true;

    }
}
