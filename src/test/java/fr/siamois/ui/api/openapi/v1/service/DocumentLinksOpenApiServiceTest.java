package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.document.DocumentLinkKind;
import fr.siamois.domain.services.document.DocumentLinkService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.FieldQuery;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.mapper.DocumentOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentLinksOpenApiServiceTest {

    private static final long TARGET_ID = 3L;
    private static final long PROJECT_ID = 7L;
    private static final long INSTITUTION_ID = 10L;

    @Mock
    private DocumentLinkService documentLinkService;
    @Mock
    private DocumentContentOpenApiService documentContentOpenApiService;
    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private ProfilePermissionService profilePermissionService;
    @Mock
    private DocumentOpenApiMapper documentOpenApiMapper;
    @Mock
    private DocumentListProjectionService documentListProjectionService;
    @Mock
    private ResourceBookmarkService resourceBookmarkService;

    private DocumentLinksOpenApiService service;
    private PersonDTO person;
    private InstitutionDTO institution;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        service = new DocumentLinksOpenApiService(documentLinkService, documentContentOpenApiService, projectApiService,
                profilePermissionService, documentOpenApiMapper, documentListProjectionService, resourceBookmarkService);
        person = new PersonDTO();
        person.setId(1L);
        institution = new InstitutionDTO();
        institution.setId(INSTITUTION_ID);
        caller = new ProjectApiCaller(person, Set.of(INSTITUTION_ID), List.of(institution));
    }

    private void targetFound(Long projectId) {
        when(documentLinkService.findTarget(DocumentLinkKind.PHASE, TARGET_ID))
                .thenReturn(Optional.of(new DocumentLinkService.Target(INSTITUTION_ID, projectId)));
    }

    private void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(status));
    }

    // --- page

    @Test
    void page_ofAnUnknownEntity_is404() {
        when(documentLinkService.findTarget(DocumentLinkKind.PHASE, TARGET_ID)).thenReturn(Optional.empty());

        assertStatus(() -> service.page(DocumentLinkKind.PHASE, TARGET_ID, caller, 0, 10, "identifier:asc", null,
                FieldQuery.NONE, null, "fr"), HttpStatus.NOT_FOUND);
    }

    @Test
    void page_ofAnEntityOutsideTheCallersInstitutions_is404() {
        when(documentLinkService.findTarget(DocumentLinkKind.PHASE, TARGET_ID))
                .thenReturn(Optional.of(new DocumentLinkService.Target(99L, PROJECT_ID)));

        assertStatus(() -> service.page(DocumentLinkKind.PHASE, TARGET_ID, caller, 0, 10, "identifier:asc", null,
                FieldQuery.NONE, null, "fr"), HttpStatus.NOT_FOUND);
        when(documentLinkService.findTarget(DocumentLinkKind.PHASE, 4L))
                .thenReturn(Optional.of(new DocumentLinkService.Target(null, PROJECT_ID)));
        assertStatus(() -> service.page(DocumentLinkKind.PHASE, 4L, caller, 0, 10, "identifier:asc", null,
                FieldQuery.NONE, null, "fr"), HttpStatus.NOT_FOUND);
    }

    @Test
    void page_ofAnEntityWhoseProjectTheCallerCannotSee_is404() {
        targetFound(PROJECT_ID);
        when(profilePermissionService.canViewProject(person, institution, PROJECT_ID)).thenReturn(false);

        assertStatus(() -> service.page(DocumentLinkKind.PHASE, TARGET_ID, caller, 0, 10, "identifier:asc", null,
                FieldQuery.NONE, null, "fr"), HttpStatus.NOT_FOUND);
        verify(projectApiService, never()).pageDocumentsLinkedTo(any(), anyLong(), any(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void page_listsTheLinkedDocumentsWithTheRightsOnEachProject() {
        targetFound(PROJECT_ID);
        when(profilePermissionService.canViewProject(person, institution, PROJECT_ID)).thenReturn(true);
        DocumentDTO doc = new DocumentDTO();
        doc.setId(5L);
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(PROJECT_ID);
        doc.setActionUnit(project);
        FieldQuery fieldQuery = FieldQuery.NONE;
        when(projectApiService.pageDocumentsLinkedTo(DocumentLinkKind.PHASE, TARGET_ID, institution, 0, 10,
                "identifier:asc", "plan", fieldQuery)).thenReturn(new PageImpl<>(List.of(doc), PageRequest.of(0, 10), 1));
        when(documentListProjectionService.build(List.of(doc), "all", "fr"))
                .thenReturn(new DocumentListProjectionService.DocumentListProjection(Map.of(), Map.of(5L, Map.of("-708", "Plan"))));
        DocumentResource resource = new DocumentResource();
        resource.setId("5");
        when(documentOpenApiMapper.toResource(doc, "fr", Map.of())).thenReturn(resource);
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), eq(PROJECT_ID),
                eq(PermissionConstants.INSTANCE_EDIT_DOCUMENTS), eq(PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS),
                eq(PermissionConstants.PROJECT_EDIT_DOCUMENTS))).thenReturn(true);
        when(profilePermissionService.hasValidatePermission(any(UserInfo.class), eq(PROJECT_ID))).thenReturn(false);

        DocumentListResponse response = service.page(DocumentLinkKind.PHASE, TARGET_ID, caller, 0, 10, "identifier:asc",
                "plan", fieldQuery, "all", "fr");

        assertThat(response.getData()).containsExactly(resource);
        assertThat(response.getMeta().total()).isEqualTo(1L);
        assertThat(resource.getAnswers()).containsEntry("-708", "Plan");
        assertThat(resource.getPermissions().canEdit()).isTrue();
        verify(resourceBookmarkService).markBookmarked(person, institution, List.of(resource), "fr");
    }

    @Test
    void page_listsADocumentWithoutProjectToo_butReadOnly() {
        when(documentLinkService.findTarget(DocumentLinkKind.PLACE, TARGET_ID))
                .thenReturn(Optional.of(new DocumentLinkService.Target(INSTITUTION_ID, null)));
        DocumentDTO orphan = new DocumentDTO();
        orphan.setId(1L);
        when(projectApiService.pageDocumentsLinkedTo(DocumentLinkKind.PLACE, TARGET_ID, institution, 0, 10,
                "creationTime:desc", null, FieldQuery.NONE)).thenReturn(new PageImpl<>(List.of(orphan), PageRequest.of(0, 10), 1));
        when(documentListProjectionService.build(List.of(orphan), null, "fr"))
                .thenReturn(DocumentListProjectionService.DocumentListProjection.empty());
        DocumentResource resource = new DocumentResource();
        resource.setId("1");
        when(documentOpenApiMapper.toResource(orphan, "fr", Map.of())).thenReturn(resource);

        DocumentListResponse response = service.page(DocumentLinkKind.PLACE, TARGET_ID, caller, 0, 10, "creationTime:desc",
                null, FieldQuery.NONE, null, "fr");

        assertThat(response.getData()).containsExactly(resource);
        assertThat(resource.getPermissions().canEdit()).isFalse();
        verify(profilePermissionService, never()).hasProjectPermission(any(), anyLong(), any(), any(), any());
    }

    @Test
    void page_ofAPlace_hasNoProjectToCheck() {
        when(documentLinkService.findTarget(DocumentLinkKind.PLACE, TARGET_ID))
                .thenReturn(Optional.of(new DocumentLinkService.Target(INSTITUTION_ID, null)));
        when(projectApiService.pageDocumentsLinkedTo(DocumentLinkKind.PLACE, TARGET_ID, institution, 0, 10,
                "identifier:asc", null, FieldQuery.NONE)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
        when(documentListProjectionService.build(List.of(), null, "fr"))
                .thenReturn(DocumentListProjectionService.DocumentListProjection.empty());

        DocumentListResponse response = service.page(DocumentLinkKind.PLACE, TARGET_ID, caller, 0, 10, "identifier:asc",
                null, FieldQuery.NONE, null, "fr");

        assertThat(response.getData()).isEmpty();
        verify(profilePermissionService, never()).canViewProject(any(), any(), anyLong());
    }

    // --- link / unlink

    @Test
    void link_addsTheLinkOfADocumentOfTheSameProject() {
        targetFound(PROJECT_ID);
        when(profilePermissionService.canViewProject(person, institution, PROJECT_ID)).thenReturn(true);
        when(documentContentOpenApiService.requireWritableDocument(5L, caller)).thenReturn(documentOfProject(PROJECT_ID));

        service.link(DocumentLinkKind.PHASE, TARGET_ID, 5L, caller);

        verify(documentLinkService).link(5L, DocumentLinkKind.PHASE, TARGET_ID);
    }

    @Test
    void link_ofADocumentOfAnotherProject_is400() {
        targetFound(PROJECT_ID);
        when(profilePermissionService.canViewProject(person, institution, PROJECT_ID)).thenReturn(true);
        when(documentContentOpenApiService.requireWritableDocument(5L, caller)).thenReturn(documentOfProject(8L));

        assertStatus(() -> service.link(DocumentLinkKind.PHASE, TARGET_ID, 5L, caller), HttpStatus.BAD_REQUEST);
        verify(documentLinkService, never()).link(anyLong(), any(), anyLong());
    }

    @Test
    void link_toAPlace_acceptsADocumentOfAnyProject() {
        when(documentLinkService.findTarget(DocumentLinkKind.PLACE, TARGET_ID))
                .thenReturn(Optional.of(new DocumentLinkService.Target(INSTITUTION_ID, null)));
        when(documentContentOpenApiService.requireWritableDocument(5L, caller)).thenReturn(documentOfProject(8L));

        service.link(DocumentLinkKind.PLACE, TARGET_ID, 5L, caller);

        verify(documentLinkService).link(5L, DocumentLinkKind.PLACE, TARGET_ID);
    }

    @Test
    void link_withoutTheEditRight_isRefusedByTheDocumentCheck() {
        targetFound(PROJECT_ID);
        when(profilePermissionService.canViewProject(person, institution, PROJECT_ID)).thenReturn(true);
        when(documentContentOpenApiService.requireWritableDocument(5L, caller))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "non"));

        assertStatus(() -> service.link(DocumentLinkKind.PHASE, TARGET_ID, 5L, caller), HttpStatus.FORBIDDEN);
        verify(documentLinkService, never()).link(anyLong(), any(), anyLong());
    }

    @Test
    void unlink_removesTheLink() {
        targetFound(PROJECT_ID);
        when(profilePermissionService.canViewProject(person, institution, PROJECT_ID)).thenReturn(true);
        when(documentContentOpenApiService.requireWritableDocument(5L, caller)).thenReturn(documentOfProject(PROJECT_ID));

        service.unlink(DocumentLinkKind.PHASE, TARGET_ID, 5L, caller);

        verify(documentLinkService).unlink(5L, DocumentLinkKind.PHASE, TARGET_ID);
    }

    private static Document documentOfProject(long projectId) {
        Document document = new Document();
        ActionUnit au = new ActionUnit();
        au.setId(projectId);
        document.setActionUnit(au);
        return document;
    }
}
