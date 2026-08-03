package fr.siamois.ui.bean.dialog.newunit;

import fr.siamois.ui.form.dto.FormUiDto;

public record UnitKindConfig(
        String resourceUri,
        String title,
        String styleClass,
        String icon,
        String autocompleteClass,
        String successMessageCode,
        String urlPrefix,
        FormUiDto customForm
) {}
