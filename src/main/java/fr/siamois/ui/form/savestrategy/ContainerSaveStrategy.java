package fr.siamois.ui.form.savestrategy;

import fr.siamois.domain.services.ContainerService;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.EntityFormContextSaveStrategy;
import fr.siamois.utils.MessageUtils;

public class ContainerSaveStrategy implements EntityFormContextSaveStrategy<ContainerDTO> {

    @Override
    public boolean save(EntityFormContext<ContainerDTO> context) {
        context.flushBackToEntity();
        ContainerDTO unit = context.getUnit();
        ContainerService service = context.getServices().getContainerService();
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