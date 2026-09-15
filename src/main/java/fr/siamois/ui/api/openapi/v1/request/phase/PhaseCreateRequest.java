package fr.siamois.ui.api.openapi.v1.request.phase;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Création minimale d'une phase depuis un champ formulaire (autocomplete "créer une nouvelle phase"),
 * miroir de {@code EntityFormContext.saveNewPhaseFromField} (JSF) : seuls le titre et l'unité d'action
 * (résolue depuis l'URL) sont vraiment requis, le reste est optionnel comme côté JSF.
 */
@Data
@Schema(description = "Création minimale d'une phase (autocomplete formulaire)")
public class PhaseCreateRequest {

    @Schema(description = "Titre de la phase.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Nullable
    @Schema(description = "Numéro d'ordre optionnel.")
    private Integer orderNumber;

    @Nullable
    @Schema(description = "Concept de type de phase, optionnel.")
    private Long typeConceptId;
}
