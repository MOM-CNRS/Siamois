package fr.siamois.ui.api.openapi.v1.controller.relation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationDefinitionService;
import fr.siamois.domain.services.relation.RelationLinkException;
import fr.siamois.domain.services.relation.RelationLinkService;
import fr.siamois.domain.services.relation.RelationRuleService;
import fr.siamois.domain.services.relation.RelationRuleSet;
import fr.siamois.domain.services.relation.RuleViolation;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.ui.api.handler.TestRestExceptionHandlers;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The REST face of the relation links: JSON shapes, side and kind names, and the status of each refusal. */
class RelationLinksControllerApiTest {

    private final ProjectApiService projectApiService = mock(ProjectApiService.class);
    private final RelationLinkService linkService = mock(RelationLinkService.class);
    private final RelationRuleService ruleService = mock(RelationRuleService.class);
    private final RelationDefinitionRepository definitionRepository = mock(RelationDefinitionRepository.class);
    private final ActionUnitRepository actionUnitRepository = mock(ActionUnitRepository.class);

    private final RelationOpenApiService service = new RelationOpenApiService(linkService,
            mock(RelationDefinitionService.class), ruleService, definitionRepository, mock(RelationLinkRepository.class),
            actionUnitRepository);

    private final PersonDTO person = new PersonDTO();
    private MockMvc mockMvc;
    private RelationDefinition order;

    @BeforeEach
    void setUp() {
        when(projectApiService.requireCaller()).thenReturn(new ProjectApiCaller(person, Set.of(3L), List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new RelationLinksControllerApi(projectApiService, service))
                .setControllerAdvice(TestRestExceptionHandlers.create())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .build();
        order = new RelationDefinition();
        order.setId(1L);
        order.setKind(RelationKind.COMPLEX);
        when(ruleService.rulesFor(1L, 7L)).thenReturn(new RelationRuleSet(order, null, true, false, false, List.of(),
                Map.of(201L, 301L), List.of()));
    }

    @Test
    void createsALinkFromTheMirrorFieldAndAnswersWithBothViewsOfTheQualifier() throws Exception {
        when(linkService.create(any(), eq(person), eq(Set.of(3L)))).thenReturn(new RelationLinkService.Result(
                link(), List.of()));

        mockMvc.perform(post("/api/v1/relation-links").contentType(MediaType.APPLICATION_JSON).content("""
                        {"definition": 1, "side": "target",
                         "record": {"resourceType": "recording-units", "resourceId": 5},
                         "other": {"resourceType": "recording-units", "resourceId": 12},
                         "qualifier": 301, "uncertain": true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source.resourceId").value(12))
                .andExpect(jsonPath("$.target.resourceType").value("recording-units"))
                .andExpect(jsonPath("$.qualifier").value(201))
                .andExpect(jsonPath("$.mirrorQualifier").value(301))
                .andExpect(jsonPath("$.warnings").doesNotExist());

        ArgumentCaptor<RelationLinkService.CreateRequest> request = ArgumentCaptor.forClass(RelationLinkService.CreateRequest.class);
        verify(linkService).create(request.capture(), eq(person), eq(Set.of(3L)));
        assertThat(request.getValue().side()).isEqualTo(RelationSide.TARGET);
        assertThat(request.getValue().edited()).isEqualTo(new RelationLinkService.End(RelationEndKind.RECORDING_UNIT, 5L));
        assertThat(request.getValue().qualifier()).isEqualTo(301L);
        assertThat(request.getValue().uncertain()).isTrue();
    }

    @Test
    void aBrokenRuleIsAConflictWithTheLinksInCause() throws Exception {
        when(linkService.create(any(), any(), any())).thenThrow(new RelationLinkException(
                RelationLinkException.Reason.RULE_VIOLATION, "Ce lien créerait une boucle",
                List.of(new RuleViolation(RuleViolation.Rule.NO_CYCLE, true, "Ce lien créerait une boucle",
                        List.of(900L, 901L)))));

        mockMvc.perform(post("/api/v1/relation-links").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("conflict"))
                .andExpect(jsonPath("$.details[0].field").value("NO_CYCLE"))
                .andExpect(jsonPath("$.details[0].message").value("Ce lien créerait une boucle (liens : 900, 901)"));
    }

    @Test
    void eachRefusalHasItsStatus() throws Exception {
        expect(RelationLinkException.Reason.ENDPOINT_NOT_FOUND, 404);
        expect(RelationLinkException.Reason.FORBIDDEN, 403);
        expect(RelationLinkException.Reason.CROSS_PROJECT, 400);
        expect(RelationLinkException.Reason.DUPLICATE, 409);
    }

    @Test
    void anUnknownSideOrResourceTypeIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/relation-links").contentType(MediaType.APPLICATION_JSON).content("""
                        {"definition": 1, "side": "left",
                         "record": {"resourceType": "recording-units", "resourceId": 5},
                         "other": {"resourceType": "recording-units", "resourceId": 12}}"""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/relation-links").contentType(MediaType.APPLICATION_JSON).content("""
                        {"definition": 1,
                         "record": {"resourceType": "people", "resourceId": 5},
                         "other": {"resourceType": "recording-units", "resourceId": 12}}"""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(linkService);
    }

    @Test
    void deletesThroughTheGivenSide() throws Exception {
        mockMvc.perform(delete("/api/v1/relation-links/900").param("side", "target"))
                .andExpect(status().isNoContent());

        verify(linkService).delete(900L, RelationSide.TARGET, person, Set.of(3L));
    }

    @Test
    void describesTheMirrorFieldWithTheQualifiersItShows() throws Exception {
        fr.siamois.domain.models.form.customfield.CustomFieldRelation source = new fr.siamois.domain.models.form.customfield.CustomFieldRelation();
        source.setId(-329L);
        fr.siamois.domain.models.form.customfield.CustomFieldRelation target = new fr.siamois.domain.models.form.customfield.CustomFieldRelation();
        target.setId(-328L);
        order.setSourceField(source);
        order.setTargetField(target);
        when(definitionRepository.findByField(-328L)).thenReturn(java.util.Optional.of(order));
        fr.siamois.domain.models.institution.Institution institution = new fr.siamois.domain.models.institution.Institution();
        institution.setId(3L);
        fr.siamois.domain.models.actionunit.ActionUnit project = new fr.siamois.domain.models.actionunit.ActionUnit();
        project.setId(7L);
        project.setCreatedByInstitution(institution);
        when(actionUnitRepository.findById(7L)).thenReturn(java.util.Optional.of(project));

        mockMvc.perform(get("/api/v1/relation-fields/-328").param("projectId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.side").value("target"))
                .andExpect(jsonPath("$.mirrorField").value(-329))
                .andExpect(jsonPath("$.qualifiers[0].shown").value(301))
                .andExpect(jsonPath("$.qualifiers[0].stored").value(201))
                .andExpect(jsonPath("$.noCycle").value(true))
                .andExpect(jsonPath("$.definition.kind").value("complex"));
    }

    @Test
    void aProjectOutOfReachIsNotFound() throws Exception {
        when(definitionRepository.findByField(-328L)).thenReturn(java.util.Optional.of(order));
        when(actionUnitRepository.findById(8L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/relation-fields/-328").param("projectId", "8"))
                .andExpect(status().isNotFound());
    }

    private void expect(RelationLinkException.Reason reason, int status) throws Exception {
        reset(linkService);
        when(linkService.create(any(), any(), any())).thenThrow(new RelationLinkException(reason, "refus"));
        mockMvc.perform(post("/api/v1/relation-links").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().is(status));
    }

    private static String body() {
        return """
                {"definition": 1,
                 "record": {"resourceType": "recording-units", "resourceId": 12},
                 "other": {"resourceType": "recording-units", "resourceId": 5}}""";
    }

    private RelationLink link() {
        RelationLink link = new RelationLink();
        link.setId(900L);
        link.setDefinition(order);
        link.setProjectId(7L);
        link.setSourceKind(RelationEndKind.RECORDING_UNIT);
        link.setSourceId(12L);
        link.setTargetKind(RelationEndKind.RECORDING_UNIT);
        link.setTargetId(5L);
        Concept qualifier = new Concept();
        qualifier.setId(201L);
        link.setQualifier(qualifier);
        link.setUncertain(true);
        return link;
    }
}
