package fr.siamois.ui.api.openapi.v1.resource.view;

import java.util.Map;

public record ViewStateResponse(String viewKey, Map<String, Object> state) {
}
