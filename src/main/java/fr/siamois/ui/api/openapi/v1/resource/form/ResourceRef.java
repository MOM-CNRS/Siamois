package fr.siamois.ui.api.openapi.v1.resource.form;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.util.Map;

@Schema(description = "Référence vers une entité liée (concept, personne, unité, etc.)")
public record ResourceRef(
        @Schema(description = "Identifiant de l'entité référencée", example = "42")
        String resourceId,

        @Schema(description = "Type de l'entité, nom de sa collection d'API : concepts | persons | action-units | "
                + "spatial-units | action-codes | recording-units | finds | phases | containers",
                example = "recording-units")
        String resourceType,

        @Schema(description = "Libellé affichable de l'entité référencée")
        @Nullable String label,

        @Schema(description = "URL de la ressource référencée dans cette API ; absent pour un type sans "
                + "endpoint de détail (personne, code d'action)", example = "/api/v1/recording-units/42")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Nullable String href,

        @Schema(description = "Ce que la relation dit de plus que sa cible — seulement pour les références "
                + "qualifiées (SELECT_MULTIPLE_STRATIGRAPHY) ; absent partout ailleurs")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Nullable StratigraphicQualifier qualifier
) {

    private static final String API_BASE = "/api/v1/";

    /** The API collection serving each resourceType's detail, when it isn't the resourceType itself. */
    private static final Map<String, String> DETAIL_COLLECTIONS = Map.of(
            "concepts", "concepts",
            "action-units", "projects",
            "spatial-units", "places",
            "recording-units", "recording-units",
            "finds", "finds",
            "phases", "phases",
            "containers", "containers");

    /** A plain reference, its {@code href} derived from its type. */
    public ResourceRef(String resourceId, String resourceType, @Nullable String label) {
        this(resourceId, resourceType, label, hrefOf(resourceType, resourceId), null);
    }

    /** A qualified reference (a stratigraphic relationship), its {@code href} derived from its type. */
    public ResourceRef(String resourceId, String resourceType, @Nullable String label,
                       @Nullable StratigraphicQualifier qualifier) {
        this(resourceId, resourceType, label, hrefOf(resourceType, resourceId), qualifier);
    }

    /** The detail URL of a resource of that type, or null for a type with no detail endpoint. */
    @Nullable
    public static String hrefOf(@Nullable String resourceType, @Nullable String resourceId) {
        String collection = resourceType == null ? null : DETAIL_COLLECTIONS.get(resourceType);
        return collection == null || resourceId == null ? null : API_BASE + collection + "/" + resourceId;
    }

    @Schema(description = "Relation stratigraphique vue depuis l'UE qui porte le champ : la référence est "
            + "l'autre UE, ceci décrit le lien")
    public record StratigraphicQualifier(
            @Schema(description = "Concept de la relation (ex. « coupe », « recouvre »)")
            @Nullable ResourceRef concept,

            @Schema(description = "Place de l'UE porteuse dans la relation stockée : unit1 ou unit2",
                    allowableValues = {"unit1", "unit2"})
            String role,

            @Schema(description = "Groupe de la relation vue depuis l'UE porteuse, déduit de role et asynchronous : "
                    + "synchronous si la relation est synchrone ; sinon posterior quand l'UE porteuse est unit1, "
                    + "anterior quand elle est unit2 — le classement des relations antérieures/postérieures/"
                    + "synchrones de la fiche", allowableValues = {"anterior", "posterior", "synchronous"})
            String position,

            @Schema(description = "Sens de lecture du concept (stratigraphic_relationship.concept_direction)")
            @Nullable Boolean conceptDirection,

            @Schema(description = "Relation asynchrone (antérieur/postérieur) ; false = synchrone")
            @Nullable Boolean asynchronous,

            @Schema(description = "Relation incertaine")
            @Nullable Boolean uncertain
    ) {
    }
}
