package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.services.ContainerService;
import fr.siamois.domain.services.PhaseService;
import fr.siamois.domain.services.document.DocumentService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.study.StudyService;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.service.FindOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService;
import fr.siamois.ui.api.openapi.v1.service.ProjectTypedCreationService.Kind;
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
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private ProjectTypedCreationService projectTypedCreationService;
    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private PhaseService phaseService;
    @Mock
    private ContainerService containerService;
    @Mock
    private StudyService studyService;
    @Mock
    private DocumentService documentService;
    @Mock
    private FindOpenApiService findOpenApiService;

    private MockMvc mockMvc;
    private PersonDTO person;

    @BeforeEach
    void setUp() {
        person = new PersonDTO();
        person.setId(1L);
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(person, SCOPE, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new IdentifierSuggestionControllerApi(projectApiService,
                projectTypedCreationService, recordingUnitService, phaseService, containerService, studyService,
                documentService, findOpenApiService)).build();
    }

    /** The shared service, faked: it hands {@code entity} to the domain suggestion the controller passes it. */
    @SuppressWarnings("unchecked")
    private <D extends AbstractEntityDTO> void sharedServiceBuilds(Kind<D> kind, String lang, D entity) {
        when(projectTypedCreationService.suggestIdentifier(eq(kind), eq("5"), eq("42"), eq(person), eq(SCOPE),
                eq(lang), any())).thenAnswer(invocation -> ((Function<D, String>) invocation.getArgument(6)).apply(entity));
    }

    @Test
    void get_forAProjectEntity_suggestsWithItsDomainService() throws Exception {
        PhaseDTO phase = new PhaseDTO();
        sharedServiceBuilds(Kind.PHASE, "fr", phase);
        when(phaseService.suggestIdentifier(phase)).thenReturn("PROJ-PH1");

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "phases")
                        .param("projectId", "5")
                        .param("typeId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedIdentifier").value("PROJ-PH1"));
    }

    @Test
    void get_forARecordingUnit_suggestsItsFullIdentifierInItsProject() throws Exception {
        RecordingUnitDTO unit = new RecordingUnitDTO();
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(5L);
        unit.setActionUnit(project);
        sharedServiceBuilds(Kind.RECORDING_UNIT, "en", unit);
        when(recordingUnitService.suggestFullIdentifier(project, unit)).thenReturn("INST-PROJ-UE1");

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "recording-units")
                        .param("projectId", "5")
                        .param("typeId", "42")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedIdentifier").value("INST-PROJ-UE1"));
    }

    @Test
    void get_forAFind_suggestsOnItsRecordingUnit() throws Exception {
        when(findOpenApiService.suggestIdentifier("187", "42", person, SCOPE, "fr")).thenReturn("UE1-M1");

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "finds")
                        .param("recordingUnitId", "187")
                        .param("typeId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedIdentifier").value("UE1-M1"));
        verifyNoInteractions(projectTypedCreationService);
    }

    @Test
    void get_withoutConfiguredFormat_returnsANullSuggestion() throws Exception {
        when(projectTypedCreationService.suggestIdentifier(eq(Kind.DOCUMENT), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "documents")
                        .param("projectId", "5")
                        .param("typeId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedIdentifier").doesNotExist());
    }

    @Test
    void get_unknownEntityType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "projects")
                        .param("projectId", "5")
                        .param("typeId", "42"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(projectTypedCreationService, findOpenApiService);
    }

    @Test
    void get_unknownType_returns404() throws Exception {
        when(projectTypedCreationService.suggestIdentifier(eq(Kind.CONTAINER), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Type de contenant introuvable"));

        mockMvc.perform(get("/api/v1/identifier-suggestions")
                        .param("entityType", "containers")
                        .param("projectId", "5")
                        .param("typeId", "42"))
                .andExpect(status().isNotFound());
    }
}
