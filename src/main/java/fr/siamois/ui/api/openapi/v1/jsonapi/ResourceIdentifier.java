package fr.siamois.ui.api.openapi.v1.jsonapi;

import lombok.AllArgsConstructor;
import lombok.Data;

public interface ResourceIdentifier {

    String getType();
    String getId();

}
