package fr.siamois.ui.api.openapi.v1.resource.project;


import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToMany;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.concept.ConceptResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class PlaceResourceIdentifier implements ResourceIdentifier {

    @Schema(description = "Resource type",
            example = "places",
            allowableValues = {"places"})
    private String resourceType;

    private String id;
}