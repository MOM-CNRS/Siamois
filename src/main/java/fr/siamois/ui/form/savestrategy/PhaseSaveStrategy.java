package fr.siamois.ui.form.savestrategy;

import fr.siamois.domain.services.PhaseService;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.EntityFormContextSaveStrategy;
import fr.siamois.utils.MessageUtils;

public class PhaseSaveStrategy implements EntityFormContextSaveStrategy<PhaseDTO> {

    @Override
    public boolean save(EntityFormContext<PhaseDTO> context) {
        context.flushBackToEntity();
        PhaseDTO unit = context.getUnit();
        PhaseService service = context.getServices().getPhaseService();
        LangBean langBean = context.getLangBean();

        try {
            service.save(unit);
        } catch (Exception e) {
            MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", unit.getIdentifier());
            return false;
        }

        return true;
    }
}
