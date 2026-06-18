package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Champ de resourceType entier (INTEGER)")
public record IntegerFieldAnswer(
        @Schema(description = "Discriminant — toujours INTEGER", example = "INTEGER")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Valeur entière saisie")
        @Nullable Integer value
) implements FieldAnswer {
}
