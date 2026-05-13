package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.response.find.FindFormData;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FindControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;

    private MockMvc mockMvc;

    private Person person;
    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        FindControllerApi controller = new FindControllerApi(projectApiService, recordingUnitOpenApiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(jsonConverter)
                .build();

        person = new Person();
        person.setId(1L);
        personDto = new PersonDTO();
        personDto.setId(1L);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                person, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getFindForm_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/finds/5/form"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(recordingUnitOpenApiService);
    }

    @Test
    void getFindForm_success_returnsJson() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L)));

        ConceptDTO type = new ConceptDTO();
        type.setId(2L);
        FindFormData payload = new FindFormData(type, null, Map.of(), Map.of());
        when(recordingUnitOpenApiService.buildFindForm(eq(5L), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/finds/5/form")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.specimenType.id").value(2));

        verify(recordingUnitOpenApiService).buildFindForm(5L, personDto, Set.of(10L), "fr");
    }

    @Test
    void getFindForm_passesAcceptLanguageToService() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L)));

        ConceptDTO type = new ConceptDTO();
        type.setId(1L);
        when(recordingUnitOpenApiService.buildFindForm(eq(9L), eq(personDto), eq(Set.of(10L)), eq("en")))
                .thenReturn(new FindFormData(type, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/finds/9/form")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildFindForm(9L, personDto, Set.of(10L), "en");
    }

    @Test
    void getFindForm_withoutAcceptLanguage_defaultsToFrench() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L)));

        ConceptDTO type = new ConceptDTO();
        type.setId(3L);
        when(recordingUnitOpenApiService.buildFindForm(eq(7L), eq(personDto), eq(Set.of(10L)), eq("fr")))
                .thenReturn(new FindFormData(type, null, Map.of(), Map.of()));

        mockMvc.perform(get("/api/v1/finds/7/form"))
                .andExpect(status().isOk());

        verify(recordingUnitOpenApiService).buildFindForm(7L, personDto, Set.of(10L), "fr");
    }

    @Test
    void getFindForm_whenNotFound_returns404() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L)));
        when(recordingUnitOpenApiService.buildFindForm(anyLong(), any(), any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Find not found"));

        mockMvc.perform(get("/api/v1/finds/99/form"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Find not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/finds/99/form"));
    }

    @Test
    void getFindForm_whenBadRequest_returns400() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L)));
        when(recordingUnitOpenApiService.buildFindForm(anyLong(), any(), any(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Find has no owning institution"));

        mockMvc.perform(get("/api/v1/finds/12/form"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Find has no owning institution"));
    }
}
