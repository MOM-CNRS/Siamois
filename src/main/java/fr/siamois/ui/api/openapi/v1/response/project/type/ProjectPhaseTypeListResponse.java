package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.type.PhaseDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.PhaseType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectPhaseTypeListResponse extends Response<List<PhaseType>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Configuration du type de phase par défaut (formulaire et identifiant) sans concept associé.")
    private final PhaseDefaultType defaultType;

    public ProjectPhaseTypeListResponse(List<PhaseType> data, PhaseDefaultType defaultType) {
        super(data);
        this.defaultType = defaultType;
    }
}
