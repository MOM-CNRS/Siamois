package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.place.PlacePatchRequest;
import fr.siamois.ui.api.openapi.v1.service.PlaceOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bulk operations on places: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/places/{id}.
 */
@Component
@RequiredArgsConstructor
public class PlaceBulkOperations implements BulkEntityOperations, BulkDocumentLinkTarget {

    private final PlaceOpenApiService placeOpenApiService;
    private final BulkPatchReader patchReader;

    /**
     * {@inheritDoc}
     */
    @Override
    public String collection() {
        return "places";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(ProjectApiCaller caller, long id, String acceptLanguage) {
        placeOpenApiService.deletePlace(caller, id, ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String acceptLanguage) {
        placeOpenApiService.updatePlace(caller, id, patchReader.read(patch, PlacePatchRequest.class), ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireEditable(ProjectApiCaller caller, long id, String acceptLanguage) {
        placeOpenApiService.requireEditable(caller, id, ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }
}
