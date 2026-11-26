package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationRuleKind;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationLinkException.Reason;
import fr.siamois.domain.services.relation.RelationRuleService.RuleSpec;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkQueries;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleExclusionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleKindRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleMirrorConceptRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RelationRuleServiceTest {

    private static final long INSTITUTION = 3L;
    private static final long PROJECT = 7L;

    private final RelationDefinitionRepository definitionRepository = mock(RelationDefinitionRepository.class);
    private final RelationRuleRepository ruleRepository = mock(RelationRuleRepository.class);
    private final RelationRuleKindRepository kindRepository = mock(RelationRuleKindRepository.class);
    private final RelationRuleMirrorConceptRepository mirrorRepository = mock(RelationRuleMirrorConceptRepository.class);
    private final RelationRuleExclusionRepository exclusionRepository = mock(RelationRuleExclusionRepository.class);
    private final RelationLinkRepository linkRepository = mock(RelationLinkRepository.class);
    private final RelationLinkQueries linkQueries = mock(RelationLinkQueries.class);
    private final RelationRuleResolver ruleResolver = mock(RelationRuleResolver.class);
    private final RelationEndpointResolver endpointResolver = mock(RelationEndpointResolver.class);
    private final RelationConfigAuthorizer configAuthorizer = mock(RelationConfigAuthorizer.class);
    private final ConceptRepository conceptRepository = mock(ConceptRepository.class);
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
    private final ActionUnitRepository actionUnitRepository = mock(ActionUnitRepository.class);

    private final RelationRuleService service = new RelationRuleService(definitionRepository, ruleRepository,
            kindRepository, mirrorRepository, exclusionRepository, linkRepository, linkQueries, ruleResolver,
            endpointResolver, configAuthorizer, conceptRepository, institutionRepository, actionUnitRepository);

    private final PersonDTO caller = new PersonDTO();
    private RelationDefinition order;
    private Institution institution;
    private ActionUnit project;

    @BeforeEach
    void setUp() {
        order = new RelationDefinition();
        order.setId(10L);
        order.setKind(RelationKind.COMPLEX);
        when(definitionRepository.findById(10L)).thenReturn(Optional.of(order));
        when(definitionRepository.existsById(11L)).thenReturn(true);
        institution = new Institution();
        institution.setId(INSTITUTION);
        project = new ActionUnit();
        project.setId(PROJECT);
        project.setCreatedByInstitution(institution);
        when(institutionRepository.findById(INSTITUTION)).thenReturn(Optional.of(institution));
        when(actionUnitRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        when(configAuthorizer.canEditRules(any(), eq(caller))).thenReturn(true);
        when(ruleRepository.save(any())).thenAnswer(i -> {
            RelationRule rule = i.getArgument(0);
            if (rule.getId() == null) rule.setId(500L);
            return rule;
        });
        when(conceptRepository.findById(anyLong())).thenAnswer(i -> Optional.of(concept(i.getArgument(0))));
        when(linkQueries.projectsReading(anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    void savingTheRulesOfAProjectWithoutItsOwnCreatesThem() {
        when(ruleRepository.findOfProject(10L, PROJECT)).thenReturn(Optional.empty());

        service.save(10L, RelationScope.project(INSTITUTION, PROJECT), spec(true, List.of(
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, 100L))), caller);

        ArgumentCaptor<RelationRule> saved = ArgumentCaptor.forClass(RelationRule.class);
        verify(ruleRepository).save(saved.capture());
        assertThat(saved.getValue().getActionUnit()).isSameAs(project);
        assertThat(saved.getValue().getInstitution()).isSameAs(institution);
        assertThat(saved.getValue().isNoCycle()).isTrue();
        ArgumentCaptor<RelationRuleKind> kind = ArgumentCaptor.forClass(RelationRuleKind.class);
        verify(kindRepository).save(kind.capture());
        assertThat(kind.getValue().getSubtype().getId()).isEqualTo(100L);
    }

    @Test
    void savingRulesThatExistReplacesTheirContent() {
        RelationRule own = new RelationRule();
        own.setId(42L);
        own.setDefinition(order);
        when(ruleRepository.findOfInstitution(10L, INSTITUTION)).thenReturn(Optional.of(own));

        service.save(10L, RelationScope.institution(INSTITUTION), spec(false, List.of()), caller);

        verify(kindRepository).deleteByRuleId(42L);
        verify(mirrorRepository).deleteByRuleId(42L);
        verify(exclusionRepository).deleteByRuleId(42L);
        verify(ruleRepository).save(own);
    }

    @Test
    void refusesWithoutTheRightToConfigureTheScope() {
        when(configAuthorizer.canEditRules(any(), eq(caller))).thenReturn(false);

        assertThatThrownBy(() -> service.save(10L, RelationScope.platform(), spec(false, List.of()), caller))
                .isInstanceOfSatisfying(RelationLinkException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.FORBIDDEN));
    }

    @Test
    void aHierarchicalRelationAlwaysForbidsCycles() {
        order.setHierarchical(true);
        when(ruleRepository.findPlatformDefault(10L)).thenReturn(Optional.empty());

        service.save(10L, RelationScope.platform(), spec(false, List.of()), caller);

        ArgumentCaptor<RelationRule> saved = ArgumentCaptor.forClass(RelationRule.class);
        verify(ruleRepository).save(saved.capture());
        assertThat(saved.getValue().isNoCycle()).isTrue();
    }

    @Test
    void mirrorPairsAreOneToOne() {
        RuleSpec spec = new RuleSpec(false, false, false, List.of(), Map.of(201L, 301L, 202L, 301L), List.of());

        assertThatThrownBy(() -> service.save(10L, RelationScope.platform(), spec, caller))
                .isInstanceOfSatisfying(RelationLinkException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.INVALID_CONFIGURATION));
    }

    @Test
    void refusesARestrictionThatExistingLinksBreak() {
        when(linkQueries.projectsReading(10L, INSTITUTION, PROJECT)).thenReturn(List.of(PROJECT));
        RelationLink toAFind = link(900L, RelationEndKind.RECORDING_UNIT, 12L, RelationEndKind.SPECIMEN, 40L);
        RelationLink between = link(901L, RelationEndKind.RECORDING_UNIT, 12L, RelationEndKind.RECORDING_UNIT, 5L);
        when(linkRepository.findOfProjects(10L, List.of(PROJECT))).thenReturn(List.of(toAFind, between));
        when(endpointResolver.findAll(eq(RelationEndKind.RECORDING_UNIT), any())).thenReturn(Map.of(
                12L, endpoint(RelationEndKind.RECORDING_UNIT, 12L), 5L, endpoint(RelationEndKind.RECORDING_UNIT, 5L)));
        when(endpointResolver.findAll(eq(RelationEndKind.SPECIMEN), any())).thenReturn(Map.of(
                40L, endpoint(RelationEndKind.SPECIMEN, 40L)));
        when(linkQueries.linksInCycles(10L, List.of(PROJECT))).thenReturn(List.of(901L));
        RuleSpec spec = new RuleSpec(true, true, false, List.of(
                new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.RECORDING_UNIT, null)),
                Map.of(), List.of());

        assertThatThrownBy(() -> service.save(10L, RelationScope.project(INSTITUTION, PROJECT), spec, caller))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.INVALID_CONFIGURATION);
                    assertThat(e.getViolations()).extracting(RuleViolation::rule).containsExactly(
                            RuleViolation.Rule.ACCEPTED_KIND, RuleViolation.Rule.SINGLE_CHOICE,
                            RuleViolation.Rule.NO_CYCLE);
                    assertThat(e.getViolations().get(0).linkIds()).containsExactly(900L);
                    assertThat(e.getViolations().get(1).linkIds()).containsExactly(900L, 901L);
                });
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void refusesAnExclusionBetweenRelationsThatAlreadyLinkTheSamePair() {
        when(linkQueries.projectsReading(10L, null, null)).thenReturn(List.of(PROJECT));
        when(linkQueries.linksSharingAPairWith(10L, 11L, List.of(PROJECT))).thenReturn(List.of(903L));
        RuleSpec spec = new RuleSpec(false, false, false, List.of(), Map.of(), List.of(11L));

        assertThatThrownBy(() -> service.save(10L, RelationScope.platform(), spec, caller))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> assertThat(e.getViolations())
                        .singleElement().satisfies(v -> assertThat(v.linkIds()).containsExactly(903L)));
    }

    @Test
    void removingAProjectsRulesIsRefusedWhenItsLinksBreakTheInstitutionsRules() {
        RelationRule own = new RelationRule();
        own.setId(42L);
        when(ruleRepository.findOfProject(10L, PROJECT)).thenReturn(Optional.of(own));
        RelationRule ofInstitution = new RelationRule();
        when(ruleRepository.findOfInstitution(10L, INSTITUTION)).thenReturn(Optional.of(ofInstitution));
        when(ruleResolver.load(ofInstitution)).thenReturn(new RelationRuleSet(order, ofInstitution, true, false,
                false, List.of(), Map.of(), List.of()));
        when(linkQueries.projectsReading(10L, INSTITUTION, PROJECT)).thenReturn(List.of(PROJECT));
        when(linkQueries.linksInCycles(10L, List.of(PROJECT))).thenReturn(List.of(901L));

        assertThatThrownBy(() -> service.delete(10L, RelationScope.project(INSTITUTION, PROJECT), caller))
                .isInstanceOf(RelationLinkException.class);
        verify(ruleRepository, never()).delete(any());
    }

    @Test
    void copiesTheRuleSetsOfAProjectOntoANewOne() {
        RelationRule original = new RelationRule();
        original.setId(42L);
        original.setDefinition(order);
        when(ruleRepository.findAllOfProject(PROJECT)).thenReturn(List.of(original));
        when(ruleResolver.load(original)).thenReturn(new RelationRuleSet(order, original, true, false, false,
                List.of(new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, null)),
                Map.of(201L, 301L), List.of(11L)));
        when(definitionRepository.findById(11L)).thenReturn(Optional.of(new RelationDefinition()));
        ActionUnit target = new ActionUnit();
        target.setId(8L);
        target.setCreatedByInstitution(institution);

        service.copyProjectRules(PROJECT, target);

        ArgumentCaptor<RelationRule> saved = ArgumentCaptor.forClass(RelationRule.class);
        verify(ruleRepository).save(saved.capture());
        assertThat(saved.getValue().getActionUnit()).isSameAs(target);
        verify(kindRepository).save(any());
        verify(mirrorRepository).save(any());
        verify(exclusionRepository).save(any());
    }

    private static RuleSpec spec(boolean noCycle, List<RelationRuleSet.AcceptedKind> kinds) {
        return new RuleSpec(noCycle, false, false, kinds, Map.of(), List.of());
    }

    private RelationLink link(long id, RelationEndKind sourceKind, long sourceId, RelationEndKind targetKind,
                              long targetId) {
        RelationLink link = new RelationLink();
        link.setId(id);
        link.setDefinition(order);
        link.setSourceKind(sourceKind);
        link.setSourceId(sourceId);
        link.setTargetKind(targetKind);
        link.setTargetId(targetId);
        return link;
    }

    private static RelationEndpoint endpoint(RelationEndKind kind, long id) {
        return new RelationEndpoint(kind, id, new Object(), PROJECT, null, INSTITUTION);
    }

    private static Concept concept(long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
    }
}
