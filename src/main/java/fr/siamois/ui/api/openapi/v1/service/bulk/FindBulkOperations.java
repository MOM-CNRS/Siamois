package fr.siamois.ui.api.openapi.v1.service.bulk;

import com.fasterxml.jackson.databind.JsonNode;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.domain.services.document.DocumentLinkService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.ui.api.openapi.v1.request.find.FindPatchRequest;
import fr.siamois.ui.api.openapi.v1.service.FindOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Bulk operations on finds: each element goes through the same service call as the unit
 * DELETE / PATCH /api/v1/finds/{id}.
 */
@Component
@RequiredArgsConstructor
public class FindBulkOperations implements BulkEntityOperations, BulkDocumentLinkTarget, BulkDuplicateTarget {

    private final FindOpenApiService findOpenApiService;
    private final SpecimenService specimenService;
    private final DocumentLinkService documentLinkService;
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
    public void delete(ProjectApiCaller caller, long id, Set<Long> selection, String lang) {
        findOpenApiService.deleteFind(id, selection, caller.person(), caller.accessibleInstitutionIds(), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void patch(ProjectApiCaller caller, long id, JsonNode patch, String lang) {
        findOpenApiService.applyPatchFind(id, patchReader.read(patch, FindPatchRequest.class),
                caller.person(), caller.accessibleInstitutionIds(), lang);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requireEditable(ProjectApiCaller caller, long id, String lang) {
        findOpenApiService.requireEditable(id, caller.person(), caller.accessibleInstitutionIds(),
                lang);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The copy keeps its source's parents, containers, phases and documents, in the same transaction.
     */
    @Override
    @Transactional
    public void duplicate(ProjectApiCaller caller, long id, String lang) {
        long copyId = Long.parseLong(findOpenApiService
                .duplicateFind(id, caller.person(), caller.accessibleInstitutionIds(), lang)
                .getId());
        specimenService.copyRelations(id, copyId);
        documentLinkService.copyLinks(DocumentLinkKind.FIND, id, copyId);
    }
}
