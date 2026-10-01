package fr.siamois.ui.api.openapi.v1.resource.form;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.List;

@Schema(description = "Champ de resourceType sélection multiple (SELECT_MULTIPLE_*). "
        + "Le champ answerType précise le resourceType des entités référencées. values n'est qu'un aperçu "
        + "si complete=false (paramètre valuesLimit) : la liste entière est à _links.values, et une écriture "
        + "doit alors passer par add/remove plutôt que par values.")
public record SelectManyFieldAnswer(
        @Schema(description = "Discriminant — SELECT_MULTIPLE_PERSON | SELECT_MULTIPLE_FROM_FIELD_CODE | "
                + "SELECT_MULTIPLE_RECORDING_UNIT | SELECT_MULTIPLE_SPATIAL_UNIT_TREE | "
                + "SELECT_MULTIPLE_SPECIMEN | SELECT_MULTIPLE_CONTAINER | SELECT_MULTIPLE_PHASE | "
                + "SELECT_MULTIPLE_STRATIGRAPHY",
                example = "SELECT_MULTIPLE_FROM_FIELD_CODE")
        String answerType,

        @Schema(description = "Définition du champ")
        FieldResource field,

        @Schema(description = "Entités sélectionnées — au plus valuesLimit, dans l'ordre de leur libellé")
        @Nullable List<ResourceRef> values,

        @Schema(description = "Nombre total de valeurs du champ", example = "37")
        long total,

        @Schema(description = "Vrai si values contient toutes les valeurs (values.length == total)")
        boolean complete,

        @Schema(description = "Présent seulement si complete=false")
        @JsonProperty("_links")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Nullable MultiValue.Links links
) implements FieldAnswer {

    /** A complete answer: every value there is. */
    public SelectManyFieldAnswer(String answerType, FieldResource field, @Nullable List<ResourceRef> values) {
        this(answerType, field, values, values == null ? 0 : values.size(), true, null);
    }

    /** The same answer as {@code value} carries, in this envelope. */
    public SelectManyFieldAnswer with(MultiValue value) {
        return new SelectManyFieldAnswer(answerType, field, value.values(), value.total(), value.complete(), value.links());
    }

    /** This answer as a bare {@link MultiValue} — what its values are, without the envelope. */
    public MultiValue asMultiValue() {
        List<ResourceRef> all = values == null ? List.of() : values;
        return new MultiValue(all, total, complete, links);
    }
}
