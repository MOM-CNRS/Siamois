package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Champ de resourceType oui/non (BOOLEAN)")
public record BooleanFieldAnswer(
        @Schema(description = "Discriminant — toujours BOOLEAN", example = "BOOLEAN")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Oui (true) ou non (false) ; null = sans réponse (seulement si le champ l'autorise : "
                + "constraints.allowNoAnswer)")
        @Nullable Boolean value
) implements FieldAnswer {
}
