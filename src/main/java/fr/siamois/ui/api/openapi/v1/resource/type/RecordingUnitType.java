package fr.siamois.ui.api.openapi.v1.resource.type;

import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class RecordingUnitType {
    private ResolvedConceptResource concept;
    private String id; // id of the concept for now
    private FormResource formBundle;
    private RecordingUnitIdentifierConfig identifierConfig;
    @Schema(description = "Champs indexés par identifiant custom_field (chaîne numérique)")
    Map<String, FieldResource> fields;
}
