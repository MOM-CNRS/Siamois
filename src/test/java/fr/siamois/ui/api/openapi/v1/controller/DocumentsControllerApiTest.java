package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentFormData;
import fr.siamois.ui.api.openapi.v1.resource.document.DocumentFormFieldApi;
import fr.siamois.ui.api.openapi.v1.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentsControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private DocumentContentOpenApiService documentContentOpenApiService;
    @Mock
    private DocumentFormOpenApiService documentFormOpenApiService;
    @Mock
    private DocumentWriteOpenApiService documentWriteOpenApiService;
    @Mock
    private DocumentOpenApiService documentOpenApiService;

    private MockMvc mockMvc;

    private Person person;
    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        DocumentsControllerApi controller = new DocumentsControllerApi(
                projectApiService,
                documentContentOpenApiService,
                documentFormOpenApiService,
                documentWriteOpenApiService,
                documentOpenApiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();

        person = new Person();
        person.setId(3L);
        personDto = new PersonDTO();
        personDto.setId(3L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void login() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                person, null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void downloadContent_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/documents/1/file"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadContent_success_returnsBinary() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        byte[] data = new byte[]{1, 2, 3};
        when(documentContentOpenApiService.requireDownloadableContent(42L, Set.of(10L)))
                .thenReturn(new DocumentContentOpenApiService.DocumentFilePayload(
                        new ByteArrayInputStream(data),
                        MediaType.APPLICATION_PDF,
                        "doc.pdf"));

        mockMvc.perform(get("/api/v1/documents/42/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("doc.pdf")))
                .andExpect(content().bytes(data));

        verify(documentContentOpenApiService).requireDownloadableContent(42L, Set.of(10L));
    }

    @Test
    void downloadContent_withDownloadFlag_isAnAttachment() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(documentContentOpenApiService.requireDownloadableContent(42L, Set.of(10L)))
                .thenReturn(new DocumentContentOpenApiService.DocumentFilePayload(
                        new ByteArrayInputStream(new byte[]{1}), MediaType.APPLICATION_PDF, "doc.pdf"));

        mockMvc.perform(get("/api/v1/documents/42/file").param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void putFile_storesTheUploadAndReturnsTheDocument() throws Exception {
        login();
        ProjectApiCaller caller = new ProjectApiCaller(personDto, Set.of(10L), List.of());
        when(projectApiService.requireCaller()).thenReturn(caller);
        fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource payload =
                new fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource();
        payload.setId("5");
        when(documentOpenApiService.getDocumentById(5L, personDto, Set.of(10L), "fr")).thenReturn(payload);
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "plan.pdf", "application/pdf", new byte[]{1, 2});

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/documents/5/file").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("5"));

        verify(documentContentOpenApiService).replaceFile(5L, file, caller);
    }

    @Test
    void putFile_withoutEditRight_returns403() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(documentContentOpenApiService.replaceFile(anyLong(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "non"));
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "plan.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/documents/5/file").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteFile_removesItAndReturnsTheDocument() throws Exception {
        login();
        ProjectApiCaller caller = new ProjectApiCaller(personDto, Set.of(10L), List.of());
        when(projectApiService.requireCaller()).thenReturn(caller);
        fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource payload =
                new fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource();
        payload.setId("5");
        when(documentOpenApiService.getDocumentById(5L, personDto, Set.of(10L), "fr")).thenReturn(payload);

        mockMvc.perform(delete("/api/v1/documents/5/file"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("5"));

        verify(documentContentOpenApiService).removeFile(5L, caller);
    }

    @Test
    void downloadContent_notFound_returns404() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(documentContentOpenApiService.requireDownloadableContent(100L, Set.of(10L)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        mockMvc.perform(get("/api/v1/documents/100/file"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void deleteDocument_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(delete("/api/v1/documents/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteDocument_success_returns204() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        mockMvc.perform(delete("/api/v1/documents/5"))
                .andExpect(status().isNoContent());

        verify(documentContentOpenApiService).deleteAccessibleDocument(eq(5L), any(ProjectApiCaller.class));
    }

    @Test
    void deleteDocument_notFound_returns404() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"))
                .when(documentContentOpenApiService).deleteAccessibleDocument(eq(99L), any(ProjectApiCaller.class));

        mockMvc.perform(delete("/api/v1/documents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void deleteDocument_withSeveralInstitutions_passesFullScope() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L, 20L), List.of()));

        mockMvc.perform(delete("/api/v1/documents/3"))
                .andExpect(status().isNoContent());

        verify(documentContentOpenApiService).deleteAccessibleDocument(
                eq(3L), argThat(c -> c.accessibleInstitutionIds().equals(Set.of(10L, 20L))));
    }

    @Test
    void getDocumentForm_withoutAuth_returns401() throws Exception {
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/documents/form").param("organizationId", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDocumentForm_orgForbidden_returns403() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation non accessible"))
                .when(projectApiService).assertOrganizationInCallerScope(99L, Set.of(10L));

        mockMvc.perform(get("/api/v1/documents/form").param("organizationId", "99"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDocumentForm_success_returnsJson() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        DocumentFormData payload = new DocumentFormData(
                List.of(new DocumentFormFieldApi("title", "TEXT", null, Document.MAX_TITLE_LENGTH)),
                null);
        when(documentFormOpenApiService.buildForm(eq(personDto), eq(10L), eq(Set.of(10L)), eq("fr"), isNull()))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/documents/form")
                        .param("organizationId", "10")
                        .header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields[0].fieldKey").value("title"));

        verify(documentFormOpenApiService).buildForm(personDto, 10L, Set.of(10L), "fr", null);
    }

    // --- the web client's endpoints (answers-based detail, creation, PATCH, siblings)

    @Test
    void getById_returnsTheResource() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource payload =
                new fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource();
        payload.setId("5");
        when(documentOpenApiService.getDocumentById(5L, personDto, Set.of(10L), "fr")).thenReturn(payload);

        mockMvc.perform(get("/api/v1/documents/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("5"));
    }

    @Test
    void createDocument_returns201() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource created =
                new fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource();
        created.setId("55");
        when(documentOpenApiService.createDocument(any(), eq(personDto), eq(Set.of(10L)), eq("fr"))).thenReturn(created);

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"1\",\"categoryId\":\"2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("55"));
    }

    @Test
    void createDocument_withoutPermission_returns403() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(documentOpenApiService.createDocument(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Création de document non autorisée sur ce projet"));

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"1\",\"categoryId\":\"2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchDocument_withAnswers_goesThroughTheFormPatch() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource patched =
                new fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource();
        patched.setId("3");
        when(documentOpenApiService.patchDocument(eq(3L), any(), eq(personDto), eq(Set.of(10L)), eq("fr"))).thenReturn(patched);

        mockMvc.perform(patch("/api/v1/documents/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"-708\":{\"value\":\"Titre\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("3"));

        verifyNoInteractions(documentWriteOpenApiService);
    }

    @Test
    void patchDocument_withFlatFields_staysOnTheMobilePath() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(documentWriteOpenApiService.updateDocument(any(), eq(3L), eq("Titre"), any(), any(), any(), any()))
                .thenReturn(new fr.siamois.ui.api.openapi.v1.resource.document.DocumentResource());

        mockMvc.perform(patch("/api/v1/documents/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Titre\"}"))
                .andExpect(status().isOk());

        verifyNoInteractions(documentOpenApiService);
    }

    @Test
    void getSiblings_returnsThem() throws Exception {
        login();
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(documentOpenApiService.findSiblings(3L, personDto, Set.of(10L)))
                .thenReturn(new fr.siamois.ui.api.openapi.v1.resource.sibling.SiblingsResource(null, null));

        mockMvc.perform(get("/api/v1/documents/3/siblings")).andExpect(status().isOk());
    }
}
