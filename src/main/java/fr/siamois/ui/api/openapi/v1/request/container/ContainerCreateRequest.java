package fr.siamois.ui.api.openapi.v1.request.container;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.lang.Nullable;

/**
 * Création minimale d'un contenant depuis un champ formulaire (autocomplete "créer un nouveau contenant"),
 * miroir de {@code EntityFormContext.saveNewContainerFromField} (JSF) : seule l'unité d'action (résolue
 * depuis l'URL) est requise, le reste est optionnel comme côté JSF. L'identifiant est auto-généré.
 */
@Data
@Schema(description = "Création minimale d'un contenant (autocomplete formulaire)")
public class ContainerCreateRequest {

    @Nullable
    @Schema(description = "Concept de type de contenant, optionnel.")
    private Long typeConceptId;

    @Nullable
    @Schema(description = "Identifiant du contenant parent, optionnel.")
    private Long parentId;
}
