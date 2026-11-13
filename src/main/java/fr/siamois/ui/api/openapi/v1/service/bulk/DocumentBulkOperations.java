package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.DocumentContentOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.DocumentOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bulk operations on documents: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/documents/{id}. Only the form part of a document (its answers and its
 * validation status) can be changed in bulk; its title and file metadata are edited one by one.
 */
@Component
@RequiredArgsConstructor
public class DocumentBulkOperations implements BulkEntityOperations {

    private final DocumentOpenApiService documentOpenApiService;
    private final DocumentContentOpenApiService documentContentOpenApiService;
    private final BulkPatchReader patchReader;

    /**
     * {@inheritDoc}
     */
    @Override
    public String collection() {
        return "documents";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(ProjectApiCaller caller, long id, String acceptLanguage) {
        documentContentOpenApiService.deleteAccessibleDocument(id, caller);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String acceptLanguage) {
        DocumentPatchRequest request = patchReader.read(patch, DocumentPatchRequest.class);
        if (!request.isFormPatch()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Seuls answers et validationStatus sont modifiables en lot sur des documents");
        }
        documentOpenApiService.applyPatchDocument(id, request, caller.person(), caller.accessibleInstitutionIds(),
                ProjectApiService.primaryAcceptLanguage(acceptLanguage));
    }
}
