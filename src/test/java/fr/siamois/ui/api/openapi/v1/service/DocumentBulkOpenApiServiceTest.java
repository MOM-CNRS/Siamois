package fr.siamois.ui.api.openapi.v1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import org.springframework.dao.DataIntegrityViolationException;
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
    void processBatch_nullManifest_throws400() {
        assertThatThrownBy(() -> service.processBatch(caller, null, Map.of(), "fr"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void processBatch_nullItemsList_throws400() {
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(null);
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

    @Test
    void parseManifest_validJson_returnsRequest() {
        DocumentBatchManifestRequest parsed = service.parseManifest("""
                {"items":[{"clientKey":"k1","title":"T","targets":[{"resourceType":"projects","id":"1"}]}]}
                """);
        assertThat(parsed.getItems()).hasSize(1);
        assertThat(parsed.getItems().get(0).getClientKey()).isEqualTo("k1");
    }

    @Test
    void parseManifest_invalidJson_throws400() {
        assertThatThrownBy(() -> service.parseManifest("{not-json"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void processBatch_tooManyItems_throws400() {
        ReflectionTestUtils.setField(service, "maxItems", 1);
        DocumentBatchItemRequest a = new DocumentBatchItemRequest();
        a.setTargets(List.of(new DocumentBatchTargetRequest("projects", "1")));
        DocumentBatchItemRequest b = new DocumentBatchItemRequest();
        b.setTargets(List.of(new DocumentBatchTargetRequest("projects", "2")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(a, b));

        assertThatThrownBy(() -> service.processBatch(caller, manifest, Map.of(), "fr"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("Trop d'items");
    }

    @Test
    void processBatch_nullItem_fails() {
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(java.util.Arrays.asList((DocumentBatchItemRequest) null));

        DocumentBatchResponse response = service.processBatch(caller, manifest, Map.of(), "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors()).contains("Item manifeste manquant");
    }

    @Test
    void processBatch_emptyTargets_fails() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setClientKey("k");
        item.setTargets(List.of());
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(caller, manifest, Map.of("file_0",
                new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())), "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors().get(0)).contains("cible");
    }

    @Test
    void processBatch_nullTargets_fails() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setClientKey("k");
        item.setTargets(null);
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(caller, manifest, Map.of("file_0",
                new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())), "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors().get(0)).contains("cible");
    }

    @Test
    void processBatch_blankResourceTypeOnly_fails() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("  ", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors().get(0)).contains("obligatoires");
    }

    @Test
    void processBatch_blankIdOnly_fails() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "  ")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors().get(0)).contains("obligatoires");
    }

    @Test
    void processBatch_findsAndPlaces_linksBoth() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);

        SpecimenDTO find = new SpecimenDTO();
        find.setId(7L);
        find.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireWritableFind(caller, "7", "fr")).thenReturn(find);

        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(8L);
        place.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireWritablePlace(caller, 8L, "fr")).thenReturn(place);

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setClientKey("row");
        item.setTitle("Photo");
        item.setTargets(List.of(
                new DocumentBatchTargetRequest("finds", "7"),
                new DocumentBatchTargetRequest("places", "8")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        Document saved = new Document();
        saved.setId(1L);
        saved.setMd5Sum("abc");
        when(documentWriteOpenApiService.saveUploadedDocument(
                any(UserInfo.class), eq("Photo"), isNull(), isNull(), isNull(), isNull(),
                eq(institution), any(MultipartFile.class)))
                .thenReturn(saved);
        when(documentWriteOpenApiService.linkIfAbsent(saved, find)).thenReturn(true);
        when(documentWriteOpenApiService.linkIfAbsent(saved, place)).thenReturn(true);
        when(documentWriteOpenApiService.toResource(saved)).thenReturn(new DocumentResource());

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("created");
        assertThat(response.getResults().get(0).getLinkedTargets()).hasSize(2);
    }

    @Test
    void processBatch_unknownResourceType_failsItem() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("unknown", "1")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors().get(0)).contains("resourceType inconnu");
    }

    @Test
    void processBatch_invalidTargetFields_collectsError() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest(null, null)));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors().get(0)).contains("?/?:");
    }

    @Test
    void processBatch_nonNumericPlaceId_fails() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("places", "abc")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getErrors().get(0)).contains("numérique");
    }

    @Test
    void processBatch_projectWithoutInstitution_fails() {
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(null);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getErrors().get(0)).contains("organisation");
    }

    @Test
    void processBatch_projectInstitutionWithoutId_fails() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(null);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getErrors().get(0)).contains("organisation");
    }

    @Test
    void processBatch_runtimeErrorOnResolve_collectsMessage() {
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenThrow(new IllegalStateException("boom"));

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getErrors().get(0)).contains("boom");
    }

    @Test
    void processBatch_dataIntegrityOnSave_skips() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(documentWriteOpenApiService.saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("dup"));

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("skipped");
        assertThat(response.getResults().get(0).getErrors().get(0)).contains("déjà présent");
    }

    @Test
    void processBatch_responseStatusOnSave_fails() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(documentWriteOpenApiService.saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad file"));

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors()).contains("bad file");
    }

    @Test
    void processBatch_runtimeOnSave_fails() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(documentWriteOpenApiService.saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("save-fail"));

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors()).contains("save-fail");
    }

    @Test
    void processBatch_dataIntegrityOnLink_countsAsSkipped() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        Document saved = new Document();
        saved.setMd5Sum("abc");
        when(documentWriteOpenApiService.saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);
        when(documentWriteOpenApiService.linkIfAbsent(saved, project))
                .thenThrow(new DataIntegrityViolationException("dup link"));
        when(documentWriteOpenApiService.toResource(saved)).thenReturn(new DocumentResource());

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("skipped");
        assertThat(response.getResults().get(0).getSkippedTargets()).hasSize(1);
    }

    @Test
    void processBatch_emptyMultipart_fails() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        MockMultipartFile empty = new MockMultipartFile("file_0", "a.pdf", "application/pdf", new byte[0]);
        DocumentBatchResponse response = service.processBatch(caller, manifest, Map.of("file_0", empty), "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors().get(0)).contains("Fichier manquant");
    }

    @Test
    void processBatch_fileReadError_fails() throws Exception {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getBytes()).thenThrow(new java.io.IOException("io"));

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(caller, manifest, Map.of("file_0", broken), "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors()).contains("Erreur lecture fichier");
    }

    @Test
    void processBatch_secondItemSameHashTarget_skipsViaBatchCache() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));

        Document saved = new Document();
        saved.setMd5Sum("abc");
        when(documentWriteOpenApiService.saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);
        when(documentWriteOpenApiService.linkIfAbsent(saved, project)).thenReturn(true);
        when(documentWriteOpenApiService.toResource(saved)).thenReturn(new DocumentResource());

        DocumentBatchItemRequest item1 = new DocumentBatchItemRequest();
        item1.setTitle("A");
        item1.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchItemRequest item2 = new DocumentBatchItemRequest();
        item2.setTitle("B");
        item2.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item1, item2));

        byte[] content = "same".getBytes();
        Map<String, MultipartFile> files = Map.of(
                "file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", content),
                "file_1", new MockMultipartFile("file_1", "b.pdf", "application/pdf", content));

        DocumentBatchResponse response = service.processBatch(caller, manifest, files, "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("created");
        assertThat(response.getResults().get(1).getStatus()).isEqualTo("skipped");
        verify(documentWriteOpenApiService, times(1)).saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void processBatch_existsByHashOnRecordingUnit_skips() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        RecordingUnitDTO ru = new RecordingUnitDTO();
        ru.setId(187L);
        ru.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireWritableRecordingUnit(caller, "187", "fr")).thenReturn(ru);
        when(documentWriteOpenApiService.existsByHash(eq(ru), anyString())).thenReturn(true);

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("recording-units", "187")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("skipped");
    }

    @Test
    void processBatch_existsByHashOnFindAndPlace_skips() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        SpecimenDTO find = new SpecimenDTO();
        find.setId(7L);
        find.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireWritableFind(caller, "7", "fr")).thenReturn(find);
        when(documentWriteOpenApiService.existsByHash(eq(find), anyString())).thenReturn(true);

        SpatialUnitDTO place = new SpatialUnitDTO();
        place.setId(8L);
        place.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireWritablePlace(caller, 8L, "fr")).thenReturn(place);
        when(documentWriteOpenApiService.existsByHash(eq(place), anyString())).thenReturn(true);

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(
                new DocumentBatchTargetRequest("finds", "7"),
                new DocumentBatchTargetRequest("places", "8")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("skipped");
        assertThat(response.getResults().get(0).getSkippedTargets()).hasSize(2);
    }

    @Test
    void processBatch_nullTargetInList_formatsAsTarget() {
        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(java.util.Arrays.asList((DocumentBatchTargetRequest) null));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors().get(0)).startsWith("target:");
    }

    @Test
    void alreadyLinkedAndLinkTarget_unknownType_returnFalse() throws Exception {
        Class<?> resolvedType = Class.forName(
                "fr.siamois.ui.api.openapi.v1.service.DocumentBulkOpenApiService$ResolvedTarget");
        var ctor = resolvedType.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object unknown = ctor.newInstance(
                "unknown", "1", new InstitutionDTO(), null, null, null, null);

        Boolean already = ReflectionTestUtils.invokeMethod(service, "alreadyLinked", "md5", unknown);
        Boolean linked = ReflectionTestUtils.invokeMethod(
                service, "linkTarget", new Document(), unknown);

        assertThat(already).isFalse();
        assertThat(linked).isFalse();
    }

    @Test
    void processBatch_responseStatusWithoutReason_usesMessage() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(10L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(5L);
        project.setCreatedByInstitution(institution);
        when(documentWriteOpenApiService.requireAccessibleProject(caller, "5"))
                .thenReturn(new AccessibleProjectForApi(project, 0L, 0L));
        when(documentWriteOpenApiService.saveUploadedDocument(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        DocumentBatchItemRequest item = new DocumentBatchItemRequest();
        item.setTitle("T");
        item.setTargets(List.of(new DocumentBatchTargetRequest("projects", "5")));
        DocumentBatchManifestRequest manifest = new DocumentBatchManifestRequest();
        manifest.setItems(List.of(item));

        DocumentBatchResponse response = service.processBatch(
                caller, manifest,
                Map.of("file_0", new MockMultipartFile("file_0", "a.pdf", "application/pdf", "x".getBytes())),
                "fr");

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("failed");
        assertThat(response.getResults().get(0).getErrors()).isNotEmpty();
    }
}
