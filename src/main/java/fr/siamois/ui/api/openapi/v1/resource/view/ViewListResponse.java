package fr.siamois.ui.api.openapi.v1.resource.view;

import java.util.List;

public record ViewListResponse(List<ViewResource> data, long totalCount, int limit, int offset) {
}
