package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer;
import fr.siamois.ui.api.openapi.v1.resource.person.PersonResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class RecordingUnitResource extends RecordingUnitResourceIdentifier {

    @Schema(description = "Révision de synchronisation (optimistic locking)")
    private Long syncRevision;

    private String identifier;
    private String fullIdentifier;
    private String projectId;

    private ResolvedConceptResource type;

    @Schema(description = "Géométrie de l'UE.")
    @Nullable
    private GeometryDTO geom;

    @Schema(description = "Valeurs de tous les champs formulaire (système et custom), indexées par fieldId. "
            + "Chaque entrée embarque sa définition (label, answerType, hint, etc.).")
    private Map<String, FieldAnswer> answers;

    @Schema(description = "Statut de validation : INCOMPLETE, COMPLETE ou VALIDATED.")
    private ValidationStatus validated;

    @Schema(description = "Date de la dernière validation, si connue (non renseignée par le simple cycle de statut aujourd'hui).")
    @Nullable
    private OffsetDateTime validatedAt;

    @Schema(description = "Auteur de la dernière validation, si connu.")
    @Nullable
    private PersonResource validatedBy;

    @JsonProperty("_counts")
    private RecordingUnitResourceCounts count;

    @JsonProperty("_links")
    private RecordingUnitResourceLinks links;

}
