package fr.siamois.ui.api.openapi.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesData;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.VocabularyOpenApiService;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VocabularyControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private VocabularyOpenApiService vocabularyOpenApiService;

    private MockMvc mockMvc;
    private PersonDTO personDto;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new VocabularyControllerApi(projectApiService, vocabularyOpenApiService))
                .setControllerAdvice(new RestExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        Person person = new Person();
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
    void listVocabularies_withoutAuth_returns401() throws Exception {
        SecurityContextHolder.clearContext();
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/vocabularies").param("organizationId", "10"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(vocabularyOpenApiService);
    }

    @Test
    void listVocabularies_orgForbidden_returns403() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation non accessible"))
                .when(projectApiService).assertOrganizationInCallerScope(eq(999L), any());

        mockMvc.perform(get("/api/v1/vocabularies").param("organizationId", "999"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vocabularyOpenApiService);
    }

    @Test
    void listVocabularies_success_returnsVocabulariesByFieldCode() throws Exception {
        when(projectApiService.requireCaller())
                .thenReturn(new ProjectApiCaller(personDto, Set.of(10L), List.of()));

        ConceptDTO concept = new ConceptDTO();
        concept.setId(7L);
        ConceptAutocompleteDTO item = new ConceptAutocompleteDTO(concept, "Label", "fr");
        VocabulariesData payload = new VocabulariesData(Map.of("SIAS.CATEGORY", List.of(item)));
        when(vocabularyOpenApiService.listVocabulariesForOrganization(10L, personDto, "fr"))
                .thenReturn(payload);

        mockMvc.perform(get("/api/v1/vocabularies")
                        .param("organizationId", "10")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vocabulariesByFieldCode['SIAS.CATEGORY']").isArray())
                .andExpect(jsonPath("$.data.vocabulariesByFieldCode['SIAS.CATEGORY'][0].originalPrefLabel").value("Label"));

        verify(vocabularyOpenApiService).listVocabulariesForOrganization(10L, personDto, "fr");
    }
}
