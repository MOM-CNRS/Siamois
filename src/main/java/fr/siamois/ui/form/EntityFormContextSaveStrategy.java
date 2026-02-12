package fr.siamois.ui.form;

import fr.siamois.domain.models.TraceableEntity;

public interface EntityFormContextSaveStrategy<T extends TraceableEntity> {
    boolean save(EntityFormContext<T> context);
}
