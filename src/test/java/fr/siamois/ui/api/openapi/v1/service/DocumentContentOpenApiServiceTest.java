package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.document.ImageThumbnailer;
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
        service = new DocumentContentOpenApiService(documentService, profilePermissionService, new ImageThumbnailer());
    }

    private static byte[] png(int width, int height) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void thumbnailOf_anImage_isAReducedJpegUnderTheSameName() throws Exception {
        var payload = new DocumentContentOpenApiService.DocumentFilePayload(
                new ByteArrayInputStream(png(800, 400)), MediaType.IMAGE_PNG, "photo.png");

        var thumbnail = service.thumbnailOf(payload, 200).orElseThrow();

        assertThat(thumbnail.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(thumbnail.fileName()).isEqualTo("photo.png");
        assertThat(javax.imageio.ImageIO.read(thumbnail.inputStream()).getWidth()).isEqualTo(200);
    }

    @Test
    void thumbnailOf_somethingThatIsNotAnImage_isEmpty() {
        var payload = new DocumentContentOpenApiService.DocumentFilePayload(
                new ByteArrayInputStream(new byte[]{1, 2}), MediaType.APPLICATION_PDF, "doc.pdf");

        assertThat(service.thumbnailOf(payload, 200)).isEmpty();
    }

    @Test
    void thumbnailOf_anImageTheJdkCannotDecode_isEmpty() {
        var payload = new DocumentContentOpenApiService.DocumentFilePayload(
                new ByteArrayInputStream(new byte[]{1, 2}), MediaType.parseMediaType("image/webp"), "photo.webp");

        assertThat(service.thumbnailOf(payload, 200)).isEmpty();
    }

    @Test
    void thumbnailOf_aStreamThatFailsToRead_isEmpty() {
        java.io.InputStream broken = new java.io.InputStream() {
            @Override
            public int read() throws java.io.IOException {
                throw new java.io.IOException("disk gone");
            }
        };
        var payload = new DocumentContentOpenApiService.DocumentFilePayload(broken, MediaType.IMAGE_PNG, "photo.png");

        assertThat(service.thumbnailOf(payload, 200)).isEmpty();
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
        when(doc.hasFile()).thenReturn(true);
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
        when(doc.hasFile()).thenReturn(true);
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
        when(profilePermissionService.hasDeleteEntitiesPermission(any(UserInfo.class), any())).thenReturn(true);

        service.deleteAccessibleDocument(4L, callerWithScope(SCOPE));

        verify(documentService).deleteDocument(same(doc));
    }

    @Test
    void deleteAccessibleDocument_withoutDeleteRightOnTheProject_throws403() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(4L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);
        when(profilePermissionService.hasDeleteEntitiesPermission(any(UserInfo.class), any())).thenReturn(false);

        ProjectApiCaller caller = callerWithScope(SCOPE);
        assertThatThrownBy(() -> service.deleteAccessibleDocument(4L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        verify(documentService, never()).deleteDocument(any());
    }

    @Test
    void requireDownloadableContent_servesTheFileUnderItsOwnName() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(11L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);
        when(doc.hasFile()).thenReturn(true);
        when(doc.getFileName()).thenReturn("Plan général.pdf");
        when(documentService.findInputStreamOfDocument(doc)).thenReturn(Optional.of(new ByteArrayInputStream(new byte[]{1})));
        when(documentService.findCompressorOf(doc)).thenReturn(new BrowserDisplayableCompressor());

        assertThat(service.requireDownloadableContent(11L, SCOPE).fileName()).isEqualTo("Plan général.pdf");
    }

    @Test
    void requireDownloadableContent_documentWithoutFile_throws404WithoutTouchingTheStorage() {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(8L)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);
        when(doc.hasFile()).thenReturn(false);

        assertThatThrownBy(() -> service.requireDownloadableContent(8L, SCOPE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
        verify(documentService, never()).findInputStreamOfDocument(any());
    }

    private Document writableDocument(long id) {
        Document doc = mock(Document.class);
        Institution inst = mock(Institution.class);
        when(documentService.findById(id)).thenReturn(Optional.of(doc));
        when(doc.getCreatedByInstitution()).thenReturn(inst);
        when(inst.getId()).thenReturn(10L);
        return doc;
    }

    @Test
    void replaceFile_storesTheUploadedFileOfAnEditableDocument() throws Exception {
        Document doc = writableDocument(4L);
        allowEditing(true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "contextPath", "/siamois");
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "plan.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(documentService.replaceFile(any(UserInfo.class), same(doc), eq("plan.pdf"), eq("application/pdf"), eq(3L), any(), eq("/siamois")))
                .thenReturn(doc);

        assertThat(service.replaceFile(4L, file, callerWithScope(SCOPE))).isSameAs(doc);
    }

    @Test
    void replaceFile_withoutAnyFile_throws400() {
        ProjectApiCaller caller = callerWithScope(SCOPE);
        org.springframework.mock.web.MockMultipartFile empty =
                new org.springframework.mock.web.MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.replaceFile(4L, empty, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(() -> service.replaceFile(4L, null, caller))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(documentService);
    }

    @Test
    void replaceFile_withoutEditRight_throws403() throws Exception {
        writableDocument(4L);
        allowEditing(false);
        ProjectApiCaller caller = callerWithScope(SCOPE);
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "plan.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> service.replaceFile(4L, file, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        verify(documentService, never()).replaceFile(any(), any(), any(), any(), anyLong(), any(), any());
    }

    @Test
    void replaceFile_aRefusedTypeOrSizeIsABadRequest() throws Exception {
        Document doc = writableDocument(4L);
        allowEditing(true);
        when(documentService.replaceFile(any(), same(doc), any(), any(), anyLong(), any(), any()))
                .thenThrow(new fr.siamois.domain.models.exceptions.InvalidFileSizeException(10L, "trop gros"));
        ProjectApiCaller caller = callerWithScope(SCOPE);
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "plan.pdf", null, new byte[]{1});

        assertThatThrownBy(() -> service.replaceFile(4L, file, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void removeFile_removesTheFileOfAnEditableDocument() {
        Document doc = writableDocument(4L);
        allowEditing(true);
        when(documentService.removeFile(doc)).thenReturn(doc);

        assertThat(service.removeFile(4L, callerWithScope(SCOPE))).isSameAs(doc);
    }

    @Test
    void removeFile_withoutEditRight_throws403() {
        writableDocument(4L);
        allowEditing(false);
        ProjectApiCaller caller = callerWithScope(SCOPE);

        assertThatThrownBy(() -> service.removeFile(4L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        verify(documentService, never()).removeFile(any());
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
