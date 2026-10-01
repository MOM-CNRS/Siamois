package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.document.compressor.BrowserDisplayableCompressor;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.PersonDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentContentOpenApiServiceTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private DocumentService documentService;
    @Mock
    private ProfilePermissionService profilePermissionService;

    private DocumentContentOpenApiService service;

    private static ProjectApiCaller callerWithScope(Set<Long> scope) {
        return new ProjectApiCaller(new PersonDTO(), scope, List.of());
    }

    @BeforeEach
    void setUp() {
        service = new DocumentContentOpenApiService(documentService, profilePermissionService);
    }

    @Test
    void requireDownloadableContent_nullScope_throws404() {
        assertThatThrownBy(() -> service.requireDownloadableContent(1L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        verifyNoMoreInteractions(documentService);
    }

    @Test
    void requireDownloadableContent_emptyScope_throws404() {
        assertThatThrownBy(() -> service.requireDownloadableContent(1L, Set.of()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        verifyNoMoreInteractions(documentService);
    }

    @Test
    void requireDownloadableContent_unknownDocument_throws404() {
        when(documentService.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireDownloadableContent(99L, SCOPE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));

        verify(documentService).findById(99L);
    }

    @Test
    void requireDownloadableContent_institutionNotInScope_throws404() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(5L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(999L);

        assertThatThrownBy(() -> service.requireDownloadableContent(5L, SCOPE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void requireDownloadableContent_missingFile_throws404() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(8L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);
        when(documentService.findInputStreamOfDocument(doc)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireDownloadableContent(8L, SCOPE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void requireDownloadableContent_applicationPdf_success() throws Exception {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(11L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);
        when(doc.getMimeType()).thenReturn("application/pdf");
        byte[] bytes = new byte[]{0x25, 0x50};
        when(documentService.findInputStreamOfDocument(doc)).thenReturn(Optional.of(new ByteArrayInputStream(bytes)));
        when(documentService.findCompressorOf(doc)).thenReturn(new BrowserDisplayableCompressor());
        when(doc.contentFileName()).thenReturn("CODE.pdf");

        DocumentContentOpenApiService.DocumentFilePayload payload = service.requireDownloadableContent(11L, SCOPE);

        assertThat(payload.mediaType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(payload.fileName()).isEqualTo("CODE.pdf");
        assertThat(payload.inputStream().readAllBytes()).isEqualTo(bytes);
    }

    @Test
    void deleteAccessibleDocument_nullScope_throws404() {
        ProjectApiCaller caller = callerWithScope(null);
        assertThatThrownBy(() -> service.deleteAccessibleDocument(1L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        verifyNoMoreInteractions(documentService);
    }

    @Test
    void deleteAccessibleDocument_emptyScope_throws404() {
        ProjectApiCaller caller = callerWithScope(Set.of());
        assertThatThrownBy(() -> service.deleteAccessibleDocument(1L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        verifyNoMoreInteractions(documentService);
    }

    @Test
    void deleteAccessibleDocument_unknown_throws404() {
        when(documentService.findById(77L)).thenReturn(Optional.empty());

        ProjectApiCaller caller = callerWithScope(SCOPE);
        assertThatThrownBy(() -> service.deleteAccessibleDocument(77L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        verify(documentService).findById(77L);
        verifyNoMoreInteractions(documentService);
    }

    @Test
    void deleteAccessibleDocument_institutionNotInScope_throws404() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(6L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(999L);

        ProjectApiCaller caller = callerWithScope(SCOPE);
        assertThatThrownBy(() -> service.deleteAccessibleDocument(6L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));

        verify(documentService).findById(6L);
        verifyNoMoreInteractions(documentService);
    }

    @Test
    void deleteAccessibleDocument_success_callsDomainDelete() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(4L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);
        allowEditing(true);

        service.deleteAccessibleDocument(4L, callerWithScope(SCOPE));

        verify(documentService).deleteDocument(same(doc));
    }

    @Test
    void deleteAccessibleDocument_withoutEditRightOnTheProject_throws403() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(4L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);
        allowEditing(false);

        ProjectApiCaller caller = callerWithScope(SCOPE);
        assertThatThrownBy(() -> service.deleteAccessibleDocument(4L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        verify(documentService, never()).deleteDocument(any());
    }

    private void allowEditing(boolean allowed) {
        when(profilePermissionService.hasProjectPermission(any(UserInfo.class), any(),
                eq(PermissionConstants.INSTANCE_EDIT_DOCUMENTS),
                eq(PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS),
                eq(PermissionConstants.PROJECT_EDIT_DOCUMENTS))).thenReturn(allowed);
    }

    @Test
    void requireAccessibleDocument_success_returnsDocument() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(4L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);

        assertThat(service.requireAccessibleDocument(4L, SCOPE)).isSameAs(doc);
        verify(documentService).findById(4L);
        verify(documentService, never()).findInputStreamOfDocument(any());
    }
}
