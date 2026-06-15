package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.List;

@Schema(description = "Valeur d'un champ de formulaire avec sa définition embarquée")
public record FieldAnswer(
        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Valeur courante — null | String | Integer | OffsetDateTime | ResourceRef. Null pour SELECT_MANY (voir values).")
        @Nullable Object value,

        @Schema(description = "Valeurs courantes pour SELECT_MANY. Null pour les autres types.")
        @Nullable List<Object> values
) {
}
