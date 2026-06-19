package fr.siamois.ui.api.openapi.v1.response.project.type;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.siamois.ui.api.openapi.v1.generic.response.Response;
import fr.siamois.ui.api.openapi.v1.resource.type.FindType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Getter
@Data
public class ProjectFindTypeListResponse extends Response<List<FindType>> {

    @JsonProperty("_default")
    @Schema(name = "_default", description = "Configuration du type de mobilier par défaut (formulaire et identifiant) sans concept associé.")
    private final FindType defaultType;

    public ProjectFindTypeListResponse(List<FindType> data, FindType defaultType) {
        super(data);
        this.defaultType = defaultType;
    }
}
