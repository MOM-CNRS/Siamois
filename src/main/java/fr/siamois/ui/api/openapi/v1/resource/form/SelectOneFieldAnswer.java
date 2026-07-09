package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

@Schema(description = "Champ de resourceType sélection unique (SELECT_ONE_*). "
        + "Le champ answerType précise l'entité référencée : concept, person, action-unit, "
        + "spatial-unit, action-code, recording-unit.")
public record SelectOneFieldAnswer(
        @Schema(description = "Discriminant — SELECT_ONE_FROM_FIELD_CODE | SELECT_ONE_PERSON | "
                + "SELECT_ONE_ACTION_UNIT | SELECT_ONE_SPATIAL_UNIT | SELECT_ONE_ACTION_CODE | "
                + "SELECT_ONE_RECORDING_UNIT | SELECT_ADDRESS",
                example = "SELECT_ONE_FROM_FIELD_CODE")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Référence vers l'entité sélectionnée")
        @Nullable ResourceRef value
) implements FieldAnswer {
}
