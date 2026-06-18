package fr.siamois.ui.api.openapi.v1.resource.recordingunit;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.FieldAnswer;
import fr.siamois.ui.api.openapi.v1.resource.form.FormResource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

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
    private String typeId;

    @Schema(description = "Géométrie de l'UE.")
    @Nullable
    private GeometryDTO geom;

    @Schema(description = "Valeurs de tous les champs formulaire (système et custom), indexées par fieldId. "
            + "Chaque entrée embarque sa définition (label, answerType, hint, etc.).")
    private Map<String, FieldAnswer> answers;

    @JsonProperty("_counts")
    private RecordingUnitResourceCounts count;

    @JsonProperty("_links")
    private RecordingUnitResourceLinks links;

}
