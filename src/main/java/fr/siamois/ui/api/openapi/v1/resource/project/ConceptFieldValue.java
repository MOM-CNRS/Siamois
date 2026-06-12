package fr.siamois.ui.api.openapi.v1.resource.project;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Valeur de champ liée à un concept : référentiel (vocabulaire) et libellé résolu pour la langue demandée.
 */
@Data
@NoArgsConstructor
public class ConceptFieldValue {

    @Schema(description = "Identifiant interne du vocabulaire (thésaurus)")
    private Long vocabularyId;

    @Schema(description = "Identifiant métier du vocabulaire (ex. code thésaurus)")
    private String vocabularyExternalId;

    @Schema(description = "URI de base du vocabulaire, si configurée")
    private String vocabularyBaseUri;

    @Schema(description = "Libellé du type de vocabulaire (ex. table SKOS)")
    private String vocabularyTypeLabel;

    @Schema(description = "Identifiant interne du concept")
    private Long conceptId;

    @Schema(description = "Identifiant métier du concept (external_id)")
    private String conceptExternalId;

    @Schema(description = "Libellé courant du concept pour la langue de la requête (Accept-Language)")
    private String displayLabel;
}
