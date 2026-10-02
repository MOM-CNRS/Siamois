package fr.siamois.ui.api.openapi.v1.resource.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.ui.api.openapi.v1.resource.BookmarkableResource;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * A document. The first block is what the mobile API has always served; the rest (identifier, category,
 * answers, permissions…) is what the web client's list and fiche read, absent from a mobile response.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DocumentResource extends DocumentResourceIdentifier implements BookmarkableResource {

    private String title;
    private String description;
    private String fileName;
    private String mimeType;
    private String url;
    private String fileCode;
    private Long size;
    @Schema(description = "Empreinte MD5 du fichier")
    private String md5Sum;

    private ConceptResourceIdentifier nature;
    private ConceptResourceIdentifier scale;
    private ConceptResourceIdentifier format;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Identifiant métier du document, unique dans son projet")
    private String identifier;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Libellé d'affichage (titre, sinon identifiant)")
    private String label;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Identifiant du projet (unité d'action) de rattachement")
    private String projectId;

    // Organization-wide list only: the row's project, for the list's "Projet" column.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Projet de rattachement (liste d'organisation seulement)")
    private ResourceRef project;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Organisation propriétaire (institution de création) : celle dont la fiche résout son catalogue de concepts")
    private OrganizationResourceIdentifier organization;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Catégorie (type de la table configurable)")
    private ResolvedConceptResource type;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Valeurs des champs formulaire, indexées par fieldId (valeurs brutes)")
    private Map<String, Object> answers;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("_permissions")
    @Schema(description = "Droits du caller sur ce document")
    private ProjectResourcePermissions permissions;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "URI de navigation/favori", example = "/document/42")
    private String resourceUri;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Le document est dans les favoris du caller")
    private Boolean bookmarked;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Statut de validation : INCOMPLETE, COMPLETE, VALIDATED, CANCELLED")
    private ValidationStatus validated;

    @Override
    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }
}
