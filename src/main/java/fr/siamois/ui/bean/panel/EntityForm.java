package fr.siamois.ui.bean.panel;

import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.ui.form.EntityFormContext;
import fr.siamois.ui.form.dto.FormUiDto;

// Common interface for single unit panel, lateral overview and new unit dialog
public interface EntityForm<T  extends AbstractEntityDTO> {

    T getUnit();
    FormUiDto getDetailsForm();
    EntityFormContext<T> getFormContext();

    // Method to initialize the form context
    void initFormContext(boolean forceInit);

}
