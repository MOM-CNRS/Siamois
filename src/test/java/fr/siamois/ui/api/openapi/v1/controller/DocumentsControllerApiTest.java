package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentFormData;
import fr.siamois.ui.api.openapi.v1.response.document.DocumentFormFieldApi;
import fr.siamois.ui.api.openapi.v1.service.DocumentContentOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.DocumentFormOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentsControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private DocumentContentOpenApiService documentContentOpenApiService;
    @Mock
    private DocumentFormOpenApiService documentFormOpenApiService;

    private MockMvc mockMvc;

    private Person person;
    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        DocumentsControllerApi controller = new DocumentsControllerApi(
                projectApiService, documentContentOpenApiService, documentFormOpenApiService);
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

        mockMvc.perform(get("/api/v1/documents/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadContent_success_returnsBinary() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        byte[] data = new byte[]{1, 2, 3};
        when(documentContentOpenApiService.requireDownloadableContent(eq(42L), eq(Set.of(10L))))
                .thenReturn(new DocumentContentOpenApiService.DocumentFilePayload(
                        new ByteArrayInputStream(data),
                        MediaType.APPLICATION_PDF,
                        "doc.pdf"));

        mockMvc.perform(get("/api/v1/documents/42"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("doc.pdf")))
                .andExpect(content().bytes(data));

        verify(documentContentOpenApiService).requireDownloadableContent(42L, Set.of(10L));
    }

    @Test
    void downloadContent_notFound_returns404() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        when(documentContentOpenApiService.requireDownloadableContent(eq(100L), eq(Set.of(10L))))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        mockMvc.perform(get("/api/v1/documents/100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
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

        verify(documentContentOpenApiService).deleteAccessibleDocument(5L, Set.of(10L));
    }

    @Test
    void deleteDocument_notFound_returns404() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"))
                .when(documentContentOpenApiService).deleteAccessibleDocument(eq(99L), eq(Set.of(10L)));

        mockMvc.perform(delete("/api/v1/documents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteDocument_withSeveralInstitutions_passesFullScope() throws Exception {
        login();
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L, 20L), List.of()));

        mockMvc.perform(delete("/api/v1/documents/3"))
                .andExpect(status().isNoContent());

        verify(documentContentOpenApiService).deleteAccessibleDocument(3L, Set.of(10L, 20L));
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
                .when(projectApiService).assertOrganizationInCallerScope(eq(99L), eq(Set.of(10L)));

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
                Map.of(Document.NATURE_FIELD_CODE, List.of()),
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
}
