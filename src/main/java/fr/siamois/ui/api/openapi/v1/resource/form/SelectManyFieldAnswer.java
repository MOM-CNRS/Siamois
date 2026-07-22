package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.List;

@Schema(description = "Champ de resourceType sélection multiple (SELECT_MULTIPLE_*). "
        + "Le champ answerType précise le resourceType des entités référencées.")
public record SelectManyFieldAnswer(
        @Schema(description = "Discriminant — SELECT_MULTIPLE_PERSON | SELECT_MULTIPLE_FROM_FIELD_CODE | "
                + "SELECT_MULTIPLE_RECORDING_UNIT | SELECT_MULTIPLE_SPATIAL_UNIT_TREE | "
                + "SELECT_MULTIPLE_SPECIMEN | SELECT_MULTIPLE_CONTAINER | SELECT_MULTIPLE_PHASE",
                example = "SELECT_MULTIPLE_FROM_FIELD_CODE")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Liste des entités sélectionnées")
        @Nullable List<ResourceRef> values
) implements FieldAnswer {
}
