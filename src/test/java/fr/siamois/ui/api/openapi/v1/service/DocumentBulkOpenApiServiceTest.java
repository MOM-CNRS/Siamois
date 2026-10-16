package fr.siamois.ui.api.openapi.v1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentBatchItemRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentBatchManifestRequest;
import fr.siamois.ui.api.openapi.v1.request.document.DocumentBatchTargetRequest;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentBatchItemResult;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentBatchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentBulkOpenApiServiceTest {

    @Mock
    private DocumentWriteOpenApiService documentWriteOpenApiService;

    private DocumentBulkOpenApiService service;

    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        service = new DocumentBulkOpenApiService(documentWriteOpenApiService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxItems", 50);

        PersonDTO person = new PersonDTO();
        person.setId(1L);
        caller = new ProjectApiCaller(person, Set.of(10L), List.of());
    }

    @Test
    void parseManifest_blank_throws400() {
        assertThatThrownBy(() -> service.parseManifest(" "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void processBatch_emptyManifest_throws400() {
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        assertThatThrownBy(() -> service.processBatch(caller, manifest, Map.of(), "fr"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void processBatch_multiTargets_linksAll() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setClientKey("row-1");
        item.setTitle("Photo");
        item.setTargets(List.of(
                new DocumentBatchTargetRequest("projects", "5"),
                new DocumentBatchTargetRequest("recording-units", "187")
        ));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(187L);
        ru.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireWritableRecordingUnit(caller, "187", "fr")).thenReturn(ru);

        Document saved = new Document();
        saved.setId(99L);
        saved.setMd5Sum("abc");
        when(documentWriteOpenApiService.saveUploadedDocument(
                any(UserInfo.class), eq("Photo"), isNull(), isNull(), isNull(), isNull(),
                eq(institution), any(MultipartFile.class)))
                .thenReturn(saved);
        when(documentWriteOpenApiService.linkIfAbsent(saved, project)).thenReturn(true);
        when(documentWriteOpenApiService.linkIfAbsent(saved, ru)).thenReturn(false);
        when(documentWriteOpenApiService.toResource(saved)).thenReturn(new DocumentResource());

        MockMultipartFile file = new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes());
        Map<String, MultipartFile> files = new HashMap<>();
        files.put("file_0", file);

        DocumentBatchResponse response = service.processBatch(caller, manifest, files, "fr");

        assertThat(response.getResults()).hasSize(1);
        DocumentBatchItemResult result = response.getResults().get(0);
        assertThat(result.getStatus()).isEqualTo("created");
        assertThat(result.getLinkedTargets()).hasSize(1);
        assertThat(result.getSkippedTargets()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void processBatch_missingFile_failsItem() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setClientKey("row-1");
        item.setTitle("Photo");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(caller, manifest, Map.of(), "fr");

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors()).isNotEmpty();
        verify(documentWriteOpenApiService, never()).saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void processBatch_allTargetsAlreadyLinked_skipsWithoutCreating() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setClientKey("row-1");
        item.setTitle("Photo");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(documentWriteOpenApiService.existsByHash(project, org.apache.commons.codec.digest.DigestUtils.md5Hex("x".getBytes())))
                .thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes());

        DocumentBatchResponse response = service.processBatch(caller, manifest, Map.of("file_0", file), "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("skipped");
        assertThat(response.getResults().get(0).getSkippedTargets()).hasSize(1);
        verify(documentWriteOpenApiService, never()).saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any());
    }
}
