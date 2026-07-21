package fr.siamois.ui.api.openapi.v1.resource.vocabulary;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Vocabulaire (thesaurus) enregistré en base")
public class VocabularyResource {

    @Schema(description = "Type de ressource", example = "vocabularies")
    private String resourceType;

    @Schema(description = "Identifiant interne vocabulary_id")
    private String id;

    @Schema(description = "Identifiant externe du thesaurus (idt)")
    private String externalId;

    @Schema(description = "URI de base du thesaurus")
    private String baseUri;

    @Schema(description = "URI complète du thesaurus (baseUri?idt=externalId)")
    private String uri;

    @Schema(description = "Type de vocabulaire (ex. Thesaurus)")
    private String typeLabel;

    @Schema(description = "Libellé du vocabulaire dans la langue demandée")
    private String label;
}
