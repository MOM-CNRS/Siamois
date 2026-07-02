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

    /**
     * Mode de rendu des champs du formulaire : {@code "input"} (éditeurs inline) ou
     * {@code "output"} (lecture seule, rendu beaucoup plus léger). Par défaut {@code "input"}
     * (dialogue de création : toujours éditable) ; les fiches ({@code AbstractSingleEntity})
     * rendent en lecture par défaut et basculent en édition à la demande — encoder ~30 éditeurs
     * PrimeFaces à chaque affichage/navigation de fiche était un des principaux coûts de rendu.
     */
    default String fieldRenderMode() {
        return "input";
    }

}
