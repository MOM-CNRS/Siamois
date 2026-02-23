package fr.siamois.ui.form;

import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.utils.MessageUtils;

public class SpatialUnitSaveStrategy implements EntityFormContextSaveStrategy<SpatialUnit> {
    @Override
    public boolean save(EntityFormContext<SpatialUnit> context) {

        context.flushBackToEntity();
        // Custom save logic for Action Unit
        SpatialUnit unit = context.getUnit();
        SpatialUnitService service = context.getSpatialUnitService();
        LangBean langBean = context.getLangBean();

        try {
            service.save(unit);
        } catch (FailedActionUnitSaveException e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", unit.getName());
            return false;
        }

        return true;

    }
}
