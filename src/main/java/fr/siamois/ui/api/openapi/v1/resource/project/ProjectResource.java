package fr.siamois.ui.api.openapi.v1.resource.project;


import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
public class ProjectResource extends ProjectResourceIdentifier {

    private String name;

    private String identifier;
    private String recordingUnitIdentifierFormat;
    private String fullIdentifier;
    private Integer maxRecordingUnitCode;
    private Integer minRecordingUnitCode;
    private String recordingUnitIdentifierLang;
    private OffsetDateTime beginDate;
    private OffsetDateTime endDate;

    private RelationshipToOne<ConceptResourceIdentifier> type;

    @Schema(description = "Code opération archéologique (Code OA), chaîne métier du code d'action principal")
    private String codeOperationArcheologique;

    @Schema(description = "Type d'opération : vocabulaire + concept + libellé courant (langue Accept-Language)")
    private ConceptFieldValue typeConcept;

    @Schema(description = "Type du code d'action (concept rattaché au Code OA), si présent")
    private ConceptFieldValue actionCodeTypeConcept;

    @Schema(description = "Catégorie du lieu principal (concept), si lieu principal renseigné")
    private ConceptFieldValue mainLocationCategoryConcept;

    private RelationshipToOne<fr.siamois.ui.api.openapi.v1.resource.project.PlaceResourceIdentifier> mainLocation ;
    private RelationshipToMany<fr.siamois.ui.api.openapi.v1.resource.project.PlaceResourceIdentifier> spatialContext ;
    private RelationshipToOne<OrganizationResourceIdentifier> organization;
    private RelationshipCountOnly children;
    private RelationshipCountOnly recordingUnitList;

    @Schema(description = "Libellé du type d'opération (thésaurus), selon la langue demandée")
    private String categorie;

    @Schema(description = "Commune / lieu principal et lieux de contexte spatial précis")
    private ProjectLocalisation localisation;

}