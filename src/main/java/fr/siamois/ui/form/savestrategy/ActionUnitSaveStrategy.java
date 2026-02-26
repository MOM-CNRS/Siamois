package fr.siamois.ui.form.savestrategy;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.EntityFormContextSaveStrategy;
import fr.siamois.utils.MessageUtils;

public class ActionUnitSaveStrategy implements EntityFormContextSaveStrategy<ActionUnitDTO> {
    @Override
    public boolean save(EntityFormContext<ActionUnitDTO> context) {

        context.flushBackToEntity();
        // Custom save logic for Action Unit
        ActionUnitDTO unit = context.getUnit();
        ActionUnitService service = context.getActionUnitService();
        LangBean langBean = context.getLangBean();

        try {
            service.save(unit);
        } catch (FailedActionUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", unit);
            return false;
        }

        return true;

    }
}
