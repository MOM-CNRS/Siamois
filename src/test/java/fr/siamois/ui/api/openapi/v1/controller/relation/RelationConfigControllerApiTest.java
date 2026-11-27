package fr.siamois.ui.api.openapi.v1.controller.relation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.services.relation.RelationDefinitionService;
import fr.siamois.domain.services.relation.RelationLinkException;
import fr.siamois.domain.services.relation.RelationLinkService;
import fr.siamois.domain.services.relation.RelationRuleService;
import fr.siamois.domain.services.relation.RelationRuleSet;
import fr.siamois.domain.services.relation.RelationScope;
import fr.siamois.domain.services.relation.RuleViolation;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.ui.api.handler.TestRestExceptionHandlers;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationDefinitionResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.RelationOpenApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The REST face of the relation configuration: definitions, and rules per scope. */
class RelationConfigControllerApiTest {

    private final ProjectApiService projectApiService = mock(ProjectApiService.class);
    private final RelationDefinitionService definitionService = mock(RelationDefinitionService.class);
    private final RelationRuleService ruleService = mock(RelationRuleService.class);
    private final ActionUnitRepository actionUnitRepository = mock(ActionUnitRepository.class);

    private final RelationOpenApiService service = new RelationOpenApiService(mock(RelationLinkService.class),
            definitionService, ruleService, mock(RelationDefinitionRepository.class), mock(RelationLinkRepository.class),
            actionUnitRepository);

    private final PersonDTO person = new PersonDTO();
    private MockMvc mockMvc;
    private RelationDefinition order;

    @BeforeEach
    void setUp() {
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(person, Set.of(3L), List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new RelationConfigControllerApi(projectApiService, service))
                .setControllerAdvice(TestRestExceptionHandlers.create())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
        order = new RelationDefinition();
        order.setId(1L);
        order.setKind(RelationKind.COMPLEX);
        CustomFieldRelation source = new CustomFieldRelation();
        source.setId(-329L);
        CustomFieldRelation target = new CustomFieldRelation();
        target.setId(-328L);
        order.setSourceField(source);
        order.setTargetField(target);
        Institution institution = new Institution();
        institution.setId(3L);
        ActionUnit project = new ActionUnit();
        project.setId(7L);
        project.setCreatedByInstitution(institution);
        when(actionUnitRepository.findById(7L)).thenReturn(Optional.of(project));
    }

    @Test
    void createsADefinitionWithItsDefaultRules() throws Exception {
        when(definitionService.create(any(), any(), eq(person))).thenReturn(order);

        mockMvc.perform(post("/api/v1/relation-definitions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sourceField": -329, "targetFieldLabel": "Antérieur à", "kind": "complex",
                         "defaultRules": {"noCycle": true,
                           "accepted": [{"side": "source", "resourceType": "recording-units", "subtype": 100}],
                           "mirrors": [{"concept": 201, "mirror": 301}]}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceField").value(-329))
                .andExpect(jsonPath("$.kind").value("complex"));

        ArgumentCaptor<RelationDefinitionService.CreateRequest> request =
                ArgumentCaptor.forClass(RelationDefinitionService.CreateRequest.class);
        verify(definitionService).create(request.capture(), eq(null), eq(person));
        RelationRuleService.RuleSpec rules = request.getValue().defaultRules();
        assertThat(rules.noCycle()).isTrue();
        assertThat(rules.kinds()).containsExactly(
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, 100L));
        assertThat(rules.mirrorByConcept()).containsExactlyEntriesOf(Map.of(201L, 301L));
    }

    @Test
    void savesTheRulesOfAProjectInItsInstitutionsScope() throws Exception {
        RelationRule rule = new RelationRule();
        rule.setActionUnit(new ActionUnit());
        when(ruleService.save(eq(1L), any(), any(), eq(person))).thenReturn(new RelationRuleSet(order, rule, true,
                false, false, List.of(), Map.of(), List.of()));

        mockMvc.perform(put("/api/v1/relation-definitions/1/rules").param("projectId", "7")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"noCycle\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("project"))
                .andExpect(jsonPath("$.own").value(true));

        verify(ruleService).save(eq(1L), eq(RelationScope.project(3L, 7L)), any(), eq(person));
    }

    @Test
    void aProjectWithoutItsOwnRulesReadsTheInheritedOnes() throws Exception {
        when(ruleService.ownRules(1L, RelationScope.project(3L, 7L))).thenReturn(Optional.empty());
        RelationRule platform = new RelationRule();
        when(ruleService.rulesFor(1L, 7L)).thenReturn(new RelationRuleSet(order, platform, true, false, false,
                List.of(), Map.of(201L, 301L), List.of(2L)));

        mockMvc.perform(get("/api/v1/relation-definitions/1/rules").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("platform"))
                .andExpect(jsonPath("$.own").value(false))
                .andExpect(jsonPath("$.mirrors[0].mirror").value(301))
                .andExpect(jsonPath("$.exclusions[0]").value(2));
    }

    @Test
    void anInstitutionOutOfReachIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/relation-definitions/1/rules").param("institutionId", "99"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(ruleService);
    }

    @Test
    void aRestrictionThatLinksBreakIsAConflictAndAnInvalidOneABadRequest() throws Exception {
        when(ruleService.save(eq(1L), any(), any(), eq(person))).thenThrow(new RelationLinkException(
                RelationLinkException.Reason.INVALID_CONFIGURATION, "Des liens existants forment des boucles",
                List.of(new RuleViolation(RuleViolation.Rule.NO_CYCLE, true, "Des liens existants forment des boucles",
                        List.of(5L)))));
        mockMvc.perform(put("/api/v1/relation-definitions/1/rules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"noCycle\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[0].message").value("Des liens existants forment des boucles (liens : 5)"));

        reset(ruleService);
        when(ruleService.save(eq(1L), any(), any(), eq(person))).thenThrow(new RelationLinkException(
                RelationLinkException.Reason.INVALID_CONFIGURATION, "Exclusion invalide : 1"));
        mockMvc.perform(put("/api/v1/relation-definitions/1/rules")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"exclusions\": [1]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMirrorPairGivenTwiceIsABadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/relation-definitions/1/rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mirrors\": [{\"concept\": 201, \"mirror\": 301}, {\"concept\": 201, \"mirror\": 302}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ruleService);
    }

    @Test
    void readUpdateAndDeleteADefinitionAndDeleteRulesGoThroughTheService() throws Exception {
        RelationOpenApiService api = mock(RelationOpenApiService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RelationConfigControllerApi(projectApiService, api))
                .setControllerAdvice(TestRestExceptionHandlers.create())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
        RelationDefinitionResponse definition = new RelationDefinitionResponse(1L, -329L, -328L, "complex", false,
                false, null, false);
        when(api.definition(1L)).thenReturn(definition);
        when(api.updateDefinition(eq(1L), any(), any(), any())).thenReturn(definition);

        mvc.perform(get("/api/v1/relation-definitions/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        mvc.perform(patch("/api/v1/relation-definitions/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"simple\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/relation-definitions/1")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/relation-definitions/1/rules").param("projectId", "7"))
                .andExpect(status().isNoContent());

        verify(api).deleteDefinition(eq(1L), eq(null), any());
        verify(api).deleteRules(eq(1L), eq(null), eq(7L), any());
    }
}
