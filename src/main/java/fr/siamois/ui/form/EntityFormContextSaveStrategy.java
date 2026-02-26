package fr.siamois.ui.form;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.dto.entity.AbstractEntityDTO;

public interface EntityFormContextSaveStrategy<T extends AbstractEntityDTO> {
    boolean save(EntityFormContext<T> context);
}
