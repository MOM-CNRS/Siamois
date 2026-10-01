package fr.siamois.ui.api.openapi.v1.resource.form;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.List;

@Schema(description = "Valeur à appliquer à un champ. Utiliser 'value' pour les scalaires ; pour un champ "
        + "multivalué, soit 'values' (remplace toute la liste), soit 'add'/'remove' (ajoute/retire des "
        + "valeurs, sans toucher aux autres) — les deux formes ne se combinent pas. add/remove est la seule "
        + "écriture sûre quand la valeur lue était incomplète (complete=false).")
public record AnswerInput(
        @Schema(description = "Valeur scalaire ou ID (String, Integer, ISO-8601 date, id numérique pour les références). null = vider le champ.")
        @Nullable Object value,

        @Schema(description = "Valeurs ou ID pour SELECT_MANY. [] = vider. null = ne pas toucher.")
        @Nullable List<Object> values,

        @Schema(description = "SELECT_MANY : ID à ajouter aux valeurs actuelles (ignorés s'ils y sont déjà)")
        @Nullable List<Object> add,

        @Schema(description = "SELECT_MANY : ID à retirer des valeurs actuelles (ignorés s'ils n'y sont pas)")
        @Nullable List<Object> remove
) {

    /** A scalar or whole-list answer. */
    public AnswerInput(@Nullable Object value, @Nullable List<Object> values) {
        this(value, values, null, null);
    }

    /** Whether this answer adds/removes values rather than setting them. */
    public boolean isDelta() {
        return add != null || remove != null;
    }
}
