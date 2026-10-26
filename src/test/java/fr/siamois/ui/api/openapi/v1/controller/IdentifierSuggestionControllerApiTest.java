package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.service.IdentifierSuggestionService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IdentifierSuggestionControllerApiTest {

    private static final Set<Long> SCOPE = Set.of(10L);

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private IdentifierSuggestionService identifierSuggestionService;

    private MockMvc mockMvc;
    private PersonDTO person;

    @BeforeEach
    void setUp() {
        person = new PersonDTO();
        person.setId(1L);
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(person, SCOPE, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new IdentifierSuggestionControllerApi(projectApiService, identifierSuggestionService)).build();
    }

    @Test
    void get_forAProjectEntity_returnsTheSuggestion() throws Exception {
        when(identifierSuggestionService.suggest("phases", "5", null, "42", person, SCOPE, "fr"))
                .thenReturn("PROJ-PH1");

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "phases")
                        .param("projectId", "5")
                        .param("typeId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedIdentifier").value("PROJ-PH1"));
    }

    @Test
    void get_forAFind_passesTheRecordingUnit_andTheAcceptLanguage() throws Exception {
        when(identifierSuggestionService.suggest("finds", null, "187", "42", person, SCOPE, "en"))
                .thenReturn("UE1-M1");

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "finds")
                        .param("recordingUnitId", "187")
                        .param("typeId", "42")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedIdentifier").value("UE1-M1"));

        verify(identifierSuggestionService).suggest("finds", null, "187", "42", person, SCOPE, "en");
    }

    @Test
    void get_withoutConfiguredFormat_returnsANullSuggestion() throws Exception {
        when(identifierSuggestionService.suggest("documents", "5", null, "42", person, SCOPE, "fr")).thenReturn(null);

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "documents")
                        .param("projectId", "5")
                        .param("typeId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedIdentifier").doesNotExist());
    }

    @Test
    void get_unknownType_returns404() throws Exception {
        when(identifierSuggestionService.suggest(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de phase introuvable"));

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "phases")
                        .param("projectId", "5")
                        .param("typeId", "42"))
                .andExpect(status().isNotFound());
    }
}
