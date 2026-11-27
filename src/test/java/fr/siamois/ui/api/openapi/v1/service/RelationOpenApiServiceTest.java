package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.customfield.CustomFieldRelation;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.relation.*;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.*;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.ui.api.openapi.v1.request.relation.*;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationFieldResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationLinkResponse;
import fr.siamois.ui.api.openapi.v1.response.relation.RelationRulesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationOpenApiServiceTest {

    private static final long INSTITUTION = 3L;
    private static final long PROJECT = 7L;
    private static final long SOURCE_FIELD = -328L;
    private static final long TARGET_FIELD = -329L;

    @Mock private RelationLinkService linkService;
    @Mock private RelationDefinitionService definitionService;
    @Mock private RelationRuleService ruleService;
    @Mock private RelationDefinitionRepository definitionRepository;
    @Mock private RelationLinkRepository linkRepository;
    @Mock private ActionUnitRepository actionUnitRepository;

    @InjectMocks private RelationOpenApiService service;

    private final ProjectApiCaller caller = new ProjectApiCaller(new PersonDTO(), Set.of(INSTITUTION), List.of());
    private RelationDefinition order;

    @BeforeEach
    void setUp() {
        order = definition(10L, SOURCE_FIELD, TARGET_FIELD, false);
        lenient().when(definitionRepository.findByField(SOURCE_FIELD)).thenReturn(Optional.of(order));
        lenient().when(definitionRepository.findByField(TARGET_FIELD)).thenReturn(Optional.of(order));
        lenient().when(actionUnitRepository.findById(PROJECT)).thenReturn(Optional.of(project(INSTITUTION)));
    }

    // ---- links ----

    @Test
    void aLinkEnteredByAFieldTakesItsDefinitionAndSideAndAnswersTheMirror() {
        when(linkService.create(any(), any(), anySet())).thenReturn(new RelationLinkService.Result(link(201L), List.of(
                new RuleViolation(RuleViolation.Rule.NO_CYCLE, false, "incertain", List.of(5L)))));
        RelationRuleSet rules = new RelationRuleSet(order, null, true, false, false, List.of(), Map.of(201L, 301L), List.of());
        when(ruleService.rulesFor(10L, PROJECT)).thenReturn(rules);

        RelationLinkResponse response = service.createLink(new RelationLinkCreateRequest(TARGET_FIELD, null, null,
                new RelationEndRequest("recording-units", 1L), new RelationEndRequest("recording-units", 2L),
                301L, true), caller);

        ArgumentCaptor<RelationLinkService.CreateRequest> request = ArgumentCaptor.forClass(RelationLinkService.CreateRequest.class);
        verify(linkService).create(request.capture(), any(), anySet());
        assertThat(request.getValue().definitionId()).isEqualTo(10L);
        assertThat(request.getValue().side()).isEqualTo(RelationSide.TARGET);
        assertThat(request.getValue().uncertain()).isTrue();
        assertThat(response.mirrorQualifier()).isEqualTo(301L);
        assertThat(response.warnings()).hasSize(1);
    }

    @Test
    void aLinkEnteredByADefinitionTakesTheGivenSide() {
        when(linkService.create(any(), any(), anySet())).thenReturn(new RelationLinkService.Result(link(null), List.of()));

        RelationLinkResponse response = service.createLink(new RelationLinkCreateRequest(null, 10L, "target",
                new RelationEndRequest("recording-units", 1L), new RelationEndRequest("recording-units", 2L),
                null, null), caller);

        ArgumentCaptor<RelationLinkService.CreateRequest> request = ArgumentCaptor.forClass(RelationLinkService.CreateRequest.class);
        verify(linkService).create(request.capture(), any(), anySet());
        assertThat(request.getValue().side()).isEqualTo(RelationSide.TARGET);
        assertThat(request.getValue().uncertain()).isFalse();
        assertThat(response.mirrorQualifier()).isNull();
    }

    @Test
    void anIncompleteLinkIsABadRequest() {
        RelationEndRequest unit = new RelationEndRequest("recording-units", 1L);
        RelationLinkCreateRequest noField = new RelationLinkCreateRequest(null, null, null, unit, unit, null, null);
        RelationLinkCreateRequest noOther = new RelationLinkCreateRequest(SOURCE_FIELD, null, null, unit, null, null, null);
        RelationLinkCreateRequest noId = new RelationLinkCreateRequest(SOURCE_FIELD, null, null, unit,
                new RelationEndRequest("recording-units", null), null, null);

        assertThatThrownBy(() -> service.createLink(null, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.createLink(noField, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.createLink(noOther, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.createLink(noId, caller)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void aFieldOfNoRelationIsNotFound() {
        RelationEndRequest unit = new RelationEndRequest("recording-units", 1L);
        RelationLinkCreateRequest body = new RelationLinkCreateRequest(99L, null, null, unit, unit, null, null);
        when(definitionRepository.findByField(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createLink(body, caller)).isInstanceOf(RelationLinkException.class);
    }

    @Test
    void aLinkIsUpdatedAndDeletedFromTheSideOfItsFieldOrTheGivenSide() {
        when(linkService.update(eq(5L), any(), any(), anySet())).thenReturn(new RelationLinkService.Result(link(null), List.of()));

        service.updateLink(5L, new RelationLinkUpdateRequest(SOURCE_FIELD, null, null, false), caller);
        service.updateLink(5L, new RelationLinkUpdateRequest(null, "target", null, true), caller);
        service.deleteLink(5L, TARGET_FIELD, null, caller);
        service.deleteLink(5L, null, "source", caller);

        verify(linkService).update(eq(5L), eq(new RelationLinkService.UpdateRequest(RelationSide.SOURCE, null, false)), any(), anySet());
        verify(linkService).update(eq(5L), eq(new RelationLinkService.UpdateRequest(RelationSide.TARGET, null, true)), any(), anySet());
        verify(linkService).delete(eq(5L), eq(RelationSide.TARGET), any(), anySet());
        verify(linkService).delete(eq(5L), eq(RelationSide.SOURCE), any(), anySet());
        assertThatThrownBy(() -> service.updateLink(5L, null, caller)).isInstanceOf(ResponseStatusException.class);
    }

    // ---- a field, as the front reads it ----

    @Test
    void theMirrorFieldOffersTheMirrorsAndAcceptsTheSourceKinds() {
        RelationRuleSet rules = new RelationRuleSet(order, null, true, true, false,
                List.of(new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, null)),
                Map.of(202L, 302L, 201L, 301L), List.of());
        when(ruleService.rulesFor(10L, PROJECT)).thenReturn(rules);

        RelationFieldResponse field = service.field(TARGET_FIELD, PROJECT, caller);

        assertThat(field.side()).isEqualTo("target");
        assertThat(field.mirrorField()).isEqualTo(SOURCE_FIELD);
        assertThat(field.singleChoice()).isFalse();
        assertThat(field.accepts()).containsExactly(new RelationFieldResponse.AcceptedEnd("recording-units", null));
        assertThat(field.qualifiers()).containsExactly(new RelationFieldResponse.Qualifier(301L, 201L),
                new RelationFieldResponse.Qualifier(302L, 202L));
        assertThat(field.noCycle()).isTrue();
    }

    @Test
    void theSourceFieldOffersTheStoredConcepts() {
        when(ruleService.rulesFor(10L, PROJECT)).thenReturn(new RelationRuleSet(order, null, false, true, false,
                List.of(), Map.of(201L, 301L), List.of()));

        RelationFieldResponse field = service.field(SOURCE_FIELD, PROJECT, caller);

        assertThat(field.side()).isEqualTo("source");
        assertThat(field.singleChoice()).isTrue();
        assertThat(field.qualifiers()).containsExactly(new RelationFieldResponse.Qualifier(201L, 201L));
    }

    @Test
    void aSymmetricFieldIsSingleChoiceWhenEitherSideIs() {
        RelationDefinition synchrony = definition(11L, -330L, -330L, true);
        when(definitionRepository.findByField(-330L)).thenReturn(Optional.of(synchrony));
        when(ruleService.rulesFor(11L, PROJECT)).thenReturn(new RelationRuleSet(synchrony, null, false, false, true,
                List.of(), Map.of(), List.of()));

        RelationFieldResponse field = service.field(-330L, PROJECT, caller);

        assertThat(field.side()).isEqualTo("source");
        assertThat(field.singleChoice()).isTrue();
    }

    @Test
    void aFieldOfAnUnreachableProjectIsNotFound() {
        when(actionUnitRepository.findById(8L)).thenReturn(Optional.of(project(99L)));

        assertThatThrownBy(() -> service.field(SOURCE_FIELD, 8L, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.field(SOURCE_FIELD, null, caller)).isInstanceOf(ResponseStatusException.class);
    }

    // ---- definitions ----

    @Test
    void definitionsAreReadCreatedUpdatedAndDeleted() {
        when(definitionRepository.findById(10L)).thenReturn(Optional.of(order));
        when(definitionService.create(any(), any())).thenReturn(order);
        when(definitionService.updateIdentity(eq(10L), any(), any())).thenReturn(order);

        assertThat(service.definition(10L).id()).isEqualTo(10L);
        service.createDefinition(new RelationDefinitionCreateRequest(SOURCE_FIELD, null, "Antérieur à", "complex",
                null, null, null, new RelationRulesRequest(true, null, null,
                List.of(new RelationRulesRequest.AcceptedKind("source", "recording-units", null)),
                List.of(new RelationRulesRequest.MirrorPair(201L, 301L)), List.of(11L))), caller);
        service.updateDefinition(10L, new RelationDefinitionUpdateRequest("simple", true, null), caller);
        service.deleteDefinition(10L, caller);

        ArgumentCaptor<RelationDefinitionService.CreateRequest> created = ArgumentCaptor.forClass(RelationDefinitionService.CreateRequest.class);
        verify(definitionService).create(created.capture(), any());
        assertThat(created.getValue().kind()).isEqualTo(RelationKind.COMPLEX);
        assertThat(created.getValue().defaultRules().noCycle()).isTrue();
        assertThat(created.getValue().defaultRules().mirrorByConcept()).containsEntry(201L, 301L);
        verify(definitionService).updateIdentity(eq(10L), eq(new RelationDefinitionService.IdentityUpdate(
                RelationKind.SIMPLE, true, null)), any());
        verify(definitionService).delete(eq(10L), any());
    }

    @Test
    void anUnknownOrIncompleteDefinitionIsRefused() {
        RelationDefinitionCreateRequest noKind = new RelationDefinitionCreateRequest(SOURCE_FIELD, null, null, null,
                null, null, null, null);
        RelationDefinitionUpdateRequest noKindUpdate = new RelationDefinitionUpdateRequest(null, null, null);
        when(definitionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definition(99L)).isInstanceOf(RelationLinkException.class);
        assertThatThrownBy(() -> service.createDefinition(null, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.createDefinition(noKind, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.updateDefinition(10L, noKindUpdate, caller)).isInstanceOf(ResponseStatusException.class);
    }

    // ---- rules ----

    @Test
    void aScopeWithItsOwnRulesShowsThem() {
        RelationRule rule = new RelationRule();
        rule.setActionUnit(project(INSTITUTION));
        when(ruleService.ownRules(10L, RelationScope.project(INSTITUTION, PROJECT))).thenReturn(Optional.of(
                new RelationRuleSet(order, rule, true, false, false,
                        List.of(new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.SPECIMEN, 4L)),
                        Map.of(202L, 302L, 201L, 301L), List.of(11L))));

        RelationRulesResponse rules = service.rules(10L, null, PROJECT, caller);

        assertThat(rules.scope()).isEqualTo("project");
        assertThat(rules.own()).isTrue();
        assertThat(rules.mirrors()).extracting(RelationRulesRequest.MirrorPair::concept).containsExactly(201L, 202L);
    }

    @Test
    void aProjectWithoutRulesShowsThoseItInherits() {
        RelationRule ofInstitution = new RelationRule();
        Institution institution = new Institution();
        institution.setId(INSTITUTION);
        ofInstitution.setInstitution(institution);
        when(ruleService.ownRules(eq(10L), any())).thenReturn(Optional.empty());
        when(ruleService.rulesFor(10L, PROJECT)).thenReturn(new RelationRuleSet(order, ofInstitution, false, false,
                false, List.of(), Map.of(), List.of()));

        RelationRulesResponse rules = service.rules(10L, INSTITUTION, PROJECT, caller);

        assertThat(rules.scope()).isEqualTo("institution");
        assertThat(rules.own()).isFalse();
    }

    @Test
    void anInstitutionReadsThePlatformDefaultOrNothing() {
        when(ruleService.ownRules(10L, RelationScope.institution(INSTITUTION))).thenReturn(Optional.empty());
        when(ruleService.ownRules(10L, RelationScope.platform())).thenReturn(Optional.of(
                new RelationRuleSet(order, new RelationRule(), false, false, false, List.of(), Map.of(), List.of())),
                Optional.empty());

        assertThat(service.rules(10L, INSTITUTION, null, caller).scope()).isEqualTo("platform");
        assertThat(service.rules(10L, INSTITUTION, null, caller).scope()).isEqualTo("none");
    }

    @Test
    void rulesAreSavedAndDeletedInTheirScope() {
        when(ruleService.save(eq(10L), eq(RelationScope.platform()), any(), any())).thenReturn(RelationRuleSet.none(order));

        RelationRulesResponse saved = service.saveRules(10L, null, null, new RelationRulesRequest(null, true, null,
                null, null, null), caller);
        service.deleteRules(10L, null, PROJECT, caller);

        assertThat(saved.scope()).isEqualTo("none");
        verify(ruleService).delete(eq(10L), eq(RelationScope.project(INSTITUTION, PROJECT)), any());
    }

    @Test
    void aWrongScopeOrMirrorPairIsRefused() {
        RelationRulesRequest twice = new RelationRulesRequest(null, null, null, null,
                List.of(new RelationRulesRequest.MirrorPair(201L, 301L), new RelationRulesRequest.MirrorPair(201L, 302L)), null);
        RelationRulesRequest incomplete = new RelationRulesRequest(null, null, null, null,
                List.of(new RelationRulesRequest.MirrorPair(201L, null)), null);

        assertThatThrownBy(() -> service.rules(10L, 99L, PROJECT, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.rules(10L, 99L, null, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.saveRules(10L, null, null, twice, caller)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.saveRules(10L, null, null, incomplete, caller)).isInstanceOf(ResponseStatusException.class);
    }

    private static RelationDefinition definition(long id, long sourceField, long targetField, boolean symmetric) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(id);
        definition.setSourceField(field(sourceField));
        definition.setTargetField(symmetric ? definition.getSourceField() : field(targetField));
        definition.setSymmetric(symmetric);
        definition.setKind(RelationKind.COMPLEX);
        return definition;
    }

    private static CustomFieldRelation field(long id) {
        CustomFieldRelation field = new CustomFieldRelation();
        field.setId(id);
        return field;
    }

    private RelationLink link(Long qualifier) {
        RelationLink link = new RelationLink();
        link.setId(5L);
        link.setDefinition(order);
        link.setProjectId(PROJECT);
        link.setSourceKind(RelationEndKind.RECORDING_UNIT);
        link.setSourceId(2L);
        link.setTargetKind(RelationEndKind.RECORDING_UNIT);
        link.setTargetId(1L);
        if (qualifier != null) {
            Concept concept = new Concept();
            concept.setId(qualifier);
            link.setQualifier(concept);
        }
        return link;
    }

    private static ActionUnit project(long institutionId) {
        Institution institution = new Institution();
        institution.setId(institutionId);
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT);
        project.setCreatedByInstitution(institution);
        return project;
    }
}
