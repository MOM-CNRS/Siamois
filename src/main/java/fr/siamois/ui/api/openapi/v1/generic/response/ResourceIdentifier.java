package fr.siamois.ui.api.openapi.v1.generic.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public interface ResourceIdentifier {

    String getResourceType();
    @JsonProperty("resourceId")
    String getId();

}
