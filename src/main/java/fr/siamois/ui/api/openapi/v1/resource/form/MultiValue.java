package fr.siamois.ui.api.openapi.v1.resource.form;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * The raw value of a multi-valued answer on a list row (and on the details that serve raw values:
 * phase, container, project) — the list counterpart of {@link SelectManyFieldAnswer}'s
 * {@code values/total/complete/_links}, minus the answer type and field definition.
 * <p>
 * It says of itself whether it is complete: a list only carries a preview of each multi-valued
 * answer ({@code valuesLimit}), and a truncated array that looked like the whole answer would get
 * written back as-is by a client, dropping every value it never saw.
 */
@Schema(description = "Valeur d'un champ multivalué : un aperçu (values) et le nombre total de valeurs. "
        + "Si complete=false, values n'est qu'un aperçu — la liste entière est à _links.values.")
public record MultiValue(
        @Schema(description = "Aperçu des valeurs, au plus valuesLimit, dans l'ordre de leur libellé")
        List<ResourceRef> values,

        @Schema(description = "Nombre total de valeurs du champ", example = "37")
        long total,

        @Schema(description = "Vrai si values contient toutes les valeurs (values.length == total)")
        boolean complete,

        @Schema(description = "Présent seulement si complete=false")
        @JsonProperty("_links")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Nullable Links links
) {

    /** A complete answer: every value there is. */
    public static MultiValue complete(List<ResourceRef> values) {
        return new MultiValue(List.copyOf(values), values.size(), true, null);
    }

    /**
     * An answer cut to its first {@code limit} values, out of {@code total}; the link is dropped
     * when nothing was cut, since there is nothing more to fetch.
     */
    public static MultiValue of(List<ResourceRef> preview, long total, int limit, @Nullable String valuesHref) {
        List<ResourceRef> kept = preview.size() > limit ? preview.subList(0, limit) : preview;
        boolean complete = kept.size() >= total;
        return new MultiValue(List.copyOf(kept), total, complete,
                complete || valuesHref == null ? null : new Links(valuesHref));
    }

    @Schema(description = "Liens d'une valeur multivaluée")
    public record Links(
            @Schema(description = "Liste paginée de toutes les valeurs du champ",
                    example = "/api/v1/recording-units/42/fields/-319/values")
            String values
    ) {
    }
}
