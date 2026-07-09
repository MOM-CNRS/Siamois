package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectRecordingUnitTypeListResponse extends Response<List<RecordingUnitType>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Configuration du type par défaut (formulaire et identifiant) sans concept associé.")
    private final RecordingUnitDefaultType defaultType;

    public ProjectRecordingUnitTypeListResponse(List<RecordingUnitType> data, RecordingUnitDefaultType defaultType) {
        super(data);
        this.defaultType = defaultType;
    }
}
