package fr.siamois.ui.api.openapi.v1.resource.find;


import fr.siamois.ui.api.openapi.v1.generic.response.geom.PointDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer;
import fr.siamois.ui.api.openapi.v1.resource.organization.OrganizationResourceIdentifier;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResourceIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FindResource extends FindResourceIdentifier {

    private String fullIdentifier;
    protected OffsetDateTime collectionDate;
    private ResolvedConceptResource type;
    private RecordingUnitResourceIdentifier recordingUnit;
    private OrganizationResourceIdentifier organization;

    @Schema(description = "Localisation de découverte du mobilier")
    @Nullable
    private PointDTO geom;

    @Schema(description = "Valeurs de tous les champs formulaire (système et custom), indexées par fieldId. "
            + "Chaque entrée embarque sa définition (label, answerType, hint, etc.).")
    private Map<String, FieldAnswer> answers;


}
