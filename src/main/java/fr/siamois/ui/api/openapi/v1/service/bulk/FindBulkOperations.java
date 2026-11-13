package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.FindOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bulk operations on finds: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/finds/{id}.
 */
@Component
@RequiredArgsConstructor
public class FindBulkOperations implements BulkEntityOperations, BulkDocumentLinkTarget {

    private final FindOpenApiService findOpenApiService;
    private final BulkPatchReader patchReader;

    /**
     * {@inheritDoc}
     */
    @Override
    public String collection() {
        return "finds";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(ProjectApiCaller caller, long id, String acceptLanguage) {
        findOpenApiService.deleteFind(id, caller.person(), caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String acceptLanguage) {
        findOpenApiService.applyPatchFind(id, patchReader.read(patch, FindPatchRequest.class),
                caller.person(), caller.accessibleInstitutionIds(), ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireEditable(ProjectApiCaller caller, long id, String acceptLanguage) {
        findOpenApiService.requireEditable(id, caller.person(), caller.accessibleInstitutionIds(),
                ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }
}
