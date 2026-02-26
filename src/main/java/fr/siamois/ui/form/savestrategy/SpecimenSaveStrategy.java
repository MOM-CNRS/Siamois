package fr.siamois.ui.form.savestrategy;

import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.EntityFormContextSaveStrategy;
import fr.siamois.utils.MessageUtils;

public class SpecimenSaveStrategy implements EntityFormContextSaveStrategy<SpecimenDTO> {
    @Override
    public boolean save(EntityFormContext<SpecimenDTO> context) {

        context.flushBackToEntity();
        // Custom save logic for Specimen
        SpecimenDTO unit = context.getUnit();
        SpecimenService service = context.getSpecimenService();
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
