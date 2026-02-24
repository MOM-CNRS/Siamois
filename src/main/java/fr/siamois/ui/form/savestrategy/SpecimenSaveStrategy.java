package fr.siamois.ui.form.savestrategy;

import fr.siamois.domain.models.exceptions.actionunit.FailedActionUnitSaveException;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.EntityFormContextSaveStrategy;
import fr.siamois.utils.MessageUtils;

public class SpecimenSaveStrategy implements EntityFormContextSaveStrategy<Specimen> {
    @Override
    public boolean save(EntityFormContext<Specimen> context) {

        context.flushBackToEntity();
        // Custom save logic for Specimen
        Specimen unit = context.getUnit();
        SpecimenService service = context.getSpecimenService();
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
