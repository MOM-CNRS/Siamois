package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.type.ContainerDefaultType;
import fr.siamois.ui.api.openapi.v1.resource.type.ContainerType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Getter
public class ProjectContainerTypeListResponse extends Response<List<ContainerType>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Configuration du type de contenant par défaut (formulaire et identifiant) sans concept associé.")
    private final ContainerDefaultType defaultType;

    public ProjectContainerTypeListResponse(List<ContainerType> data, ContainerDefaultType defaultType) {
        super(data);
        this.defaultType = defaultType;
    }
}
