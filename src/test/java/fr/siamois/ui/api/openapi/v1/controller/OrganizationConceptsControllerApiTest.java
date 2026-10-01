package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.organization.OrganizationProjectsControllerApi;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RecordingUnitOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.VocabularyOpenApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@code GET /api/v1/organizations/{id}/concepts} (plan §3 phase 3) — the org-scoped sibling of
 * {@code GET /api/v1/projects/{id}/concepts} (see {@link ProjectConceptsControllerApiTest}, whose
 * conventions this mirrors), needed because the list's per-column filter widgets have an
 * organization in scope but no project to derive one from.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationConceptsControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private RecordingUnitOpenApiService recordingUnitOpenApiService;
    @Mock
    private VocabularyOpenApiService vocabularyOpenApiService;

    private MockMvc mockMvc;

    private PersonDTO personDto;
    private ProjectApiCaller caller;

    @BeforeEach
    void setUp() {
        OrganizationProjectsControllerApi controller = new OrganizationProjectsControllerApi(
                projectApiService, recordingUnitOpenApiService, vocabularyOpenApiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();

        personDto = new PersonDTO();
        personDto.setId(7L);

        InstitutionDTO institutionDto = new InstitutionDTO();
        institutionDto.setId(100L);
        caller = new ProjectApiCaller(personDto, Set.of(100L), List.of(institutionDto));
    }

    private ConceptAutocompleteDTO conceptAutocomplete(long conceptId, String prefLabel) {
        ConceptDTO concept = new ConceptDTO();
        concept.setId(conceptId);
        concept.setExternalId("EXT-" + conceptId);
        return new ConceptAutocompleteDTO(concept, prefLabel, "fr");
    }

    @Test
    void getConcepts_withoutAuthentication_returns401() throws Exception {
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/organizations/100/concepts").param("fieldCode", "SIARU.TYPE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getConcepts_emptyFieldCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/100/concepts").param("fieldCode", ""))
                .andExpect(status().isBadRequest());

        verify(projectApiService, never()).requireCaller();
    }

    @Test
    void getConcepts_organizationOutOfScope_returns403() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Organisation non accessible"))
                .when(projectApiService).assertOrganizationInCallerScope(999L, Set.of(100L));

        mockMvc.perform(get("/api/v1/organizations/999/concepts").param("fieldCode", "SIARU.TYPE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getConcepts_noVocabularyConfiguredForFieldCode_returns404() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(vocabularyOpenApiService.getConceptsForOrganization(eq(100L), eq("SIARU.UNKNOWN"), isNull(), anyString(), eq(personDto)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun vocabulaire configuré pour le fieldCode : SIARU.UNKNOWN"));

        mockMvc.perform(get("/api/v1/organizations/100/concepts").param("fieldCode", "SIARU.UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getConcepts_suggestMode_returnsUnpaginatedResultsWithTotalCountHeader() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        List<ConceptAutocompleteDTO> all = List.of(
                conceptAutocomplete(1L, "Céramique"),
                conceptAutocomplete(2L, "Céramique fine"),
                conceptAutocomplete(3L, "Céramique commune"));
        when(vocabularyOpenApiService.getConceptsForOrganization(eq(100L), eq("SIARU.TYPE"), eq("cera"), anyString(), eq(personDto)))
                .thenReturn(all);

        mockMvc.perform(get("/api/v1/organizations/100/concepts").param("fieldCode", "SIARU.TYPE").param("q", "cera"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void getConcepts_pagedMode_appliesOffsetAndLimit() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        List<ConceptAutocompleteDTO> all = List.of(
                conceptAutocomplete(1L, "A"), conceptAutocomplete(2L, "B"), conceptAutocomplete(3L, "C"));
        when(vocabularyOpenApiService.getConceptsForOrganization(eq(100L), eq("SIARU.TYPE"), isNull(), anyString(), eq(personDto)))
                .thenReturn(all);

        mockMvc.perform(get("/api/v1/organizations/100/concepts")
                        .param("fieldCode", "SIARU.TYPE").param("offset", "1").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].resolvedLabel").value("B"));
    }
}
