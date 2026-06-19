package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.generic.response.ListResponse;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.concept.ResolvedConceptResource;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@Getter
public class ProjectRecordingUnitTypeListResponse extends Response<List<ResolvedConceptResource>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Type par défaut configuré pour ce projet, sans concept associé.")
    private final ResolvedConceptResource defaultType;

    public ProjectRecordingUnitTypeListResponse(List<ResolvedConceptResource> data, ResolvedConceptResource defaultType) {
        super(data);
        this.defaultType = defaultType;
    }
}
