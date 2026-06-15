package fr.siamois.ui.api.openapi.v1.resource.type;

import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.FormResource;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RecordingUnitType {
    private ResolvedConceptResource concept;
    private String resourceId; // id of the concept for now, or _default might evolve if we add a type table
    private FormResource formBundle;
    private IdentifierConfig identifierConfig;
}
