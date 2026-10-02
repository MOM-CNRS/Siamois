package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.domain.services.document.DocumentLinkService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.FieldQuery;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.ListMeta;
import fr.siamois.ui.api.openapi.v1.mapper.DocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.resource.project.ProjectResourcePermissions;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Documents tab of the other entities' fiches: the documents linked to a recording unit, a find, a
 * place, a phase or a container, and linking / unlinking one. The caller needs to see the entity, and —
 * to change a link — the document edit right on the document's project.
 */
@Service
@RequiredArgsConstructor
public class DocumentLinksOpenApiService {

    private final DocumentLinkService documentLinkService;
    private final DocumentContentOpenApiService documentContentOpenApiService;
    private final ProjectApiService projectApiService;
    private final ProfilePermissionService profilePermissionService;
    private final DocumentOpenApiMapper documentOpenApiMapper;
    private final DocumentListProjectionService documentListProjectionService;
    private final ResourceBookmarkService resourceBookmarkService;

    /** The entity as the caller sees it: found, inside their institutions, and its project viewable. */
    private DocumentLinkService.Target requireViewableTarget(DocumentLinkKind kind, long targetId, ProjectApiCaller caller) {
        DocumentLinkService.Target target = documentLinkService.findTarget(kind, targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élément introuvable"));
        InstitutionDTO institution = institutionOf(target, caller);
        if (institution == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Élément introuvable ou hors périmètre");
        }
        if (target.projectId() != null
                && !profilePermissionService.canViewProject(caller.person(), institution, target.projectId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Élément introuvable ou hors périmètre");
        }
        return target;
    }

    private static InstitutionDTO institutionOf(DocumentLinkService.Target target, ProjectApiCaller caller) {
        if (target.institutionId() == null || !caller.accessibleInstitutionIds().contains(target.institutionId())) {
            return null;
        }
        return caller.institutions().stream()
                .filter(i -> Objects.equals(i.getId(), target.institutionId()))
                .findFirst()
                .orElseGet(() -> {
                    InstitutionDTO institution = new InstitutionDTO();
                    institution.setId(target.institutionId());
                    return institution;
                });
    }

    @Transactional(readOnly = true)
    public DocumentListResponse page(DocumentLinkKind kind, long targetId, ProjectApiCaller caller, int offset, int limit,
                                     String sort, String search, FieldQuery fieldQuery,
                                     String fields, String lang) {
        DocumentLinkService.Target target = requireViewableTarget(kind, targetId, caller);
        InstitutionDTO institution = institutionOf(target, caller);
        Page<DocumentDTO> page = projectApiService.pageDocumentsLinkedTo(
                kind, targetId, institution, offset, limit, sort, search, fieldQuery);

        DocumentListProjectionService.DocumentListProjection projection =
                documentListProjectionService.build(page.getContent(), fields, lang);
        // A page can hold documents of several projects (a place is shared): one right check per project.
        Map<Long, ProjectResourcePermissions> permissionsByProject = new HashMap<>();
        List<DocumentResource> resources = page.getContent().stream()
                .map(dto -> {
                    DocumentResource resource = documentOpenApiMapper.toResource(dto, lang, projection.resolvedLabels());
                    if (fields != null) resource.setAnswers(projection.answersFor(dto.getId()));
                    // A document no migration could attach to a project has no project to hold a right on.
                    Long projectId = dto.getActionUnit() == null ? null : dto.getActionUnit().getId();
                    resource.setPermissions(permissionsByProject.computeIfAbsent(
                            projectId, id -> permissionsOn(id, institution, caller, lang)));
                    return resource;
                })
                .toList();
        resourceBookmarkService.markBookmarked(caller.person(), institution, resources, lang);
        return new DocumentListResponse(resources, new ListMeta(page.getTotalElements(), limit, (long) offset));
    }

    private ProjectResourcePermissions permissionsOn(Long projectId, InstitutionDTO institution,
                                                     ProjectApiCaller caller, String lang) {
        if (projectId == null) {
            return ProjectResourcePermissions.of(false);
        }
        UserInfo userInfo = new UserInfo(institution, caller.person(), lang);
        boolean canEdit = profilePermissionService.hasProjectPermission(userInfo, projectId,
                PermissionConstants.INSTANCE_EDIT_DOCUMENTS,
                PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS,
                PermissionConstants.PROJECT_EDIT_DOCUMENTS);
        return ProjectResourcePermissions.of(canEdit).withValidate(profilePermissionService.hasValidatePermission(userInfo, projectId));
    }

    /** Idempotent: linking a document that is already linked changes nothing. */
    @Transactional
    public void link(DocumentLinkKind kind, long targetId, long documentId, ProjectApiCaller caller) {
        DocumentLinkService.Target target = requireViewableTarget(kind, targetId, caller);
        Document document = documentContentOpenApiService.requireWritableDocument(documentId, caller);
        Long documentProject = document.getActionUnit() == null ? null : document.getActionUnit().getId();
        if (target.projectId() != null && !Objects.equals(target.projectId(), documentProject)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le document et l'élément n'appartiennent pas au même projet");
        }
        documentLinkService.link(documentId, kind, targetId);
    }

    /** Idempotent: unlinking a document that is not linked changes nothing. */
    @Transactional
    public void unlink(DocumentLinkKind kind, long targetId, long documentId, ProjectApiCaller caller) {
        requireViewableTarget(kind, targetId, caller);
        documentContentOpenApiService.requireWritableDocument(documentId, caller);
        documentLinkService.unlink(documentId, kind, targetId);
    }
}
