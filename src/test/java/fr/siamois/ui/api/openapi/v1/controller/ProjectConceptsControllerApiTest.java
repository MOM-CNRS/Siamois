package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.handler.RestExceptionHandler;
import fr.siamois.ui.api.openapi.v1.controller.project.ProjectConceptsControllerApi;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.VocabularyOpenApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectConceptsControllerApiTest {

    @Mock
    private ProjectApiService projectApiService;
    @Mock
    private VocabularyOpenApiService vocabularyOpenApiService;

    private MockMvc mockMvc;

    private PersonDTO personDto;
    private ProjectApiCaller caller;
    private InstitutionDTO institutionDto;

    @BeforeEach
    void setUp() {
        ProjectConceptsControllerApi controller =
                new ProjectConceptsControllerApi(projectApiService, vocabularyOpenApiService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();

        personDto = new PersonDTO();
        personDto.setId(7L);

        institutionDto = new InstitutionDTO();
        institutionDto.setId(100L);

        caller = new ProjectApiCaller(personDto, Set.of(100L), List.of(institutionDto));
    }

    private void stubAccessibleProject(long projectId, InstitutionDTO institution) {
        when(projectApiService.requireCaller()).thenReturn(caller);
        ActionUnitDTO au = new ActionUnitDTO();
        au.setId(projectId);
        au.setCreatedByInstitution(institution);
        when(projectApiService.requireAccessibleProject(caller, String.valueOf(projectId)))
                .thenReturn(new AccessibleProjectForApi(au, 0, 0));
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

        mockMvc.perform(get("/api/v1/projects/9/concepts").param("fieldCode", "SIARU.TYPE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getConcepts_emptyFieldCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/projects/9/concepts").param("fieldCode", ""))
                .andExpect(status().isBadRequest());

        verify(projectApiService, Mockito.never()).requireCaller();
    }

    @Test
    void getConcepts_blankFieldCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/projects/9/concepts").param("fieldCode", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getConcepts_projectNotAccessible_returns404() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(projectApiService.requireAccessibleProject(caller, "9"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable ou non accessible"));

        mockMvc.perform(get("/api/v1/projects/9/concepts").param("fieldCode", "SIARU.TYPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getConcepts_projectWithoutOrganization_returns400() throws Exception {
        stubAccessibleProject(9L, null);

        mockMvc.perform(get("/api/v1/projects/9/concepts").param("fieldCode", "SIARU.TYPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getConcepts_noVocabularyConfiguredForFieldCode_returns404() throws Exception {
        stubAccessibleProject(9L, institutionDto);
        when(vocabularyOpenApiService.getConceptsForOrganization(eq(100L), eq("SIARU.UNKNOWN"), isNull(), anyString(), eq(personDto)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun vocabulaire configuré pour le fieldCode : SIARU.UNKNOWN"));

        mockMvc.perform(get("/api/v1/projects/9/concepts").param("fieldCode", "SIARU.UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getConcepts_suggestMode_returnsUnpaginatedResultsWithTotalCountHeader() throws Exception {
        stubAccessibleProject(9L, institutionDto);
        List<ConceptAutocompleteDTO> all = List.of(
                conceptAutocomplete(1L, "Céramique"),
                conceptAutocomplete(2L, "Céramique fine"),
                conceptAutocomplete(3L, "Céramique commune"));
        when(vocabularyOpenApiService.getConceptsForOrganization(eq(100L), eq("SIARU.TYPE"), eq("cera"), anyString(), eq(personDto)))
                .thenReturn(all);

        mockMvc.perform(get("/api/v1/projects/9/concepts")
                        .param("fieldCode", "SIARU.TYPE")
                        .param("q", "cera")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].resolvedLabel").value("Céramique"));
    }

    @Test
    void getConcepts_pagedMode_appliesOffsetAndLimit() throws Exception {
        stubAccessibleProject(9L, institutionDto);
        List<ConceptAutocompleteDTO> all = List.of(
                conceptAutocomplete(1L, "A"),
                conceptAutocomplete(2L, "B"),
                conceptAutocomplete(3L, "C"));
        when(vocabularyOpenApiService.getConceptsForOrganization(eq(100L), eq("SIARU.TYPE"), isNull(), anyString(), eq(personDto)))
                .thenReturn(all);

        mockMvc.perform(get("/api/v1/projects/9/concepts")
                        .param("fieldCode", "SIARU.TYPE")
                        .param("offset", "1")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].resolvedLabel").value("B"));
    }

    @Test
    void getConcepts_offsetBeyondSize_returnsEmptyPage() throws Exception {
        stubAccessibleProject(9L, institutionDto);
        List<ConceptAutocompleteDTO> all = List.of(conceptAutocomplete(1L, "A"));
        when(vocabularyOpenApiService.getConceptsForOrganization(eq(100L), eq("SIARU.TYPE"), isNull(), anyString(), eq(personDto)))
                .thenReturn(all);

        mockMvc.perform(get("/api/v1/projects/9/concepts")
                        .param("fieldCode", "SIARU.TYPE")
                        .param("offset", "5")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void getConcepts_defaultPagination_usesOffsetZeroAndLimit50() throws Exception {
        stubAccessibleProject(9L, institutionDto);
        when(vocabularyOpenApiService.getConceptsForOrganization(eq(100L), eq("SIARU.TYPE"), isNull(), anyString(), eq(personDto)))
                .thenReturn(List.of(conceptAutocomplete(1L, "A")));

        mockMvc.perform(get("/api/v1/projects/9/concepts").param("fieldCode", "SIARU.TYPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void getFieldCodes_withoutAuthentication_returns401() throws Exception {
        when(projectApiService.requireCaller())
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise"));

        mockMvc.perform(get("/api/v1/projects/9/field-codes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFieldCodes_projectNotAccessible_returns404() throws Exception {
        when(projectApiService.requireCaller()).thenReturn(caller);
        when(projectApiService.requireAccessibleProject(caller, "9"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Projet introuvable ou non accessible"));

        mockMvc.perform(get("/api/v1/projects/9/field-codes"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFieldCodes_projectWithoutOrganization_returns400() throws Exception {
        stubAccessibleProject(9L, null);

        mockMvc.perform(get("/api/v1/projects/9/field-codes"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFieldCodes_success_returnsAvailableCodes() throws Exception {
        stubAccessibleProject(9L, institutionDto);
        when(vocabularyOpenApiService.getAvailableFieldCodesForOrganization(eq(100L), anyString(), eq(personDto)))
                .thenReturn(List.of("SIARU.TYPE", "SIAS.CATEGORY"));

        mockMvc.perform(get("/api/v1/projects/9/field-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0]").value("SIARU.TYPE"))
                .andExpect(jsonPath("$.data[1]").value("SIAS.CATEGORY"));
    }
}
