package fr.siamois.ui.api.openapi.v1.generic.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface ResourceIdentifier {

    String getResourceType();
    @JsonProperty("resourceId")
    String getId();

}
