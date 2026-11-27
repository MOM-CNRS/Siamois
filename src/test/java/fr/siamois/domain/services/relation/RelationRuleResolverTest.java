package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.relation.*;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleExclusionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleKindRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleMirrorConceptRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** A project reads its own rule set, else its institution's, else the platform default (docs/modele-relations.md, §3.3). */
@ExtendWith(MockitoExtension.class)
class RelationRuleResolverTest {

    private static final long DEFINITION = 10L;
    private static final long PROJECT = 7L;
    private static final long INSTITUTION = 3L;

    @Mock private RelationRuleRepository ruleRepository;
    @Mock private RelationRuleKindRepository kindRepository;
    @Mock private RelationRuleMirrorConceptRepository mirrorRepository;
    @Mock private RelationRuleExclusionRepository exclusionRepository;
    @Mock private ActionUnitRepository actionUnitRepository;

    @InjectMocks private RelationRuleResolver resolver;

    private final RelationDefinition definition = definition(DEFINITION);

    @BeforeEach
    void noContentByDefault() {
        lenient().when(kindRepository.findByRuleId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
        lenient().when(mirrorRepository.findByRuleId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
        lenient().when(exclusionRepository.findByRuleId(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
    }

    @Test
    void aProjectWithItsOwnRulesReadsThem() {
        RelationRule own = rule(1L);
        when(ruleRepository.findOfProject(DEFINITION, PROJECT)).thenReturn(Optional.of(own));

        assertThat(resolver.rulesFor(definition, PROJECT).rule()).isSameAs(own);
        verifyNoInteractions(actionUnitRepository);
    }

    @Test
    void elseItReadsItsInstitutions() {
        RelationRule ofInstitution = rule(2L);
        when(ruleRepository.findOfProject(DEFINITION, PROJECT)).thenReturn(Optional.empty());
        when(actionUnitRepository.findById(PROJECT)).thenReturn(Optional.of(project()));
        when(ruleRepository.findOfInstitution(DEFINITION, INSTITUTION)).thenReturn(Optional.of(ofInstitution));

        assertThat(resolver.storedRuleFor(definition, PROJECT)).contains(ofInstitution);
    }

    @Test
    void elseThePlatformDefault() {
        RelationRule platform = rule(3L);
        when(ruleRepository.findOfProject(DEFINITION, PROJECT)).thenReturn(Optional.empty());
        when(actionUnitRepository.findById(PROJECT)).thenReturn(Optional.of(project()));
        when(ruleRepository.findOfInstitution(DEFINITION, INSTITUTION)).thenReturn(Optional.empty());
        when(ruleRepository.findPlatformDefault(DEFINITION)).thenReturn(Optional.of(platform));

        assertThat(resolver.storedRuleFor(definition, PROJECT)).contains(platform);
    }

    @Test
    void anUnknownProjectSkipsTheInstitutionAndNoRuleSetRestrictsNothing() {
        when(ruleRepository.findOfProject(DEFINITION, PROJECT)).thenReturn(Optional.empty());
        when(actionUnitRepository.findById(PROJECT)).thenReturn(Optional.empty());
        when(ruleRepository.findPlatformDefault(DEFINITION)).thenReturn(Optional.empty());

        RelationRuleSet rules = resolver.rulesFor(definition, PROJECT);

        assertThat(rules).isEqualTo(RelationRuleSet.none(definition));
        verify(ruleRepository, org.mockito.Mockito.never()).findOfInstitution(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void loadReadsTheKindsMirrorPairsAndExclusionsOfARuleSet() {
        RelationRule rule = rule(4L);
        rule.setNoCycle(true);
        rule.setSourceUnique(true);
        RelationRuleKind kind = new RelationRuleKind();
        kind.setSide(RelationSide.SOURCE);
        kind.setKind(RelationEndKind.RECORDING_UNIT);
        kind.setSubtype(concept(900L));
        RelationRuleKind anyType = new RelationRuleKind();
        anyType.setSide(RelationSide.TARGET);
        anyType.setKind(RelationEndKind.SPECIMEN);
        RelationRuleMirrorConcept pair = new RelationRuleMirrorConcept();
        pair.setConcept(concept(201L));
        pair.setMirrorConcept(concept(202L));
        RelationRuleExclusion exclusion = new RelationRuleExclusion();
        exclusion.setExcludedDefinition(definition(11L));
        when(kindRepository.findByRuleId(4L)).thenReturn(List.of(kind, anyType));
        when(mirrorRepository.findByRuleId(4L)).thenReturn(List.of(pair));
        when(exclusionRepository.findByRuleId(4L)).thenReturn(List.of(exclusion));

        RelationRuleSet rules = resolver.load(rule);

        assertThat(rules.noCycle()).isTrue();
        assertThat(rules.sourceUnique()).isTrue();
        assertThat(rules.targetUnique()).isFalse();
        assertThat(rules.kinds()).containsExactly(
                new RelationRuleSet.AcceptedKind(RelationSide.SOURCE, RelationEndKind.RECORDING_UNIT, 900L),
                new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.SPECIMEN, null));
        assertThat(rules.mirrorByConcept()).isEqualTo(Map.of(201L, 202L));
        assertThat(rules.excludedDefinitionIds()).containsExactly(11L);
    }

    private RelationRule rule(long id) {
        RelationRule rule = new RelationRule();
        rule.setId(id);
        rule.setDefinition(definition);
        return rule;
    }

    private static RelationDefinition definition(long id) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(id);
        return definition;
    }

    private static ActionUnit project() {
        Institution institution = new Institution();
        institution.setId(INSTITUTION);
        ActionUnit project = new ActionUnit();
        project.setId(PROJECT);
        project.setCreatedByInstitution(institution);
        return project;
    }

    private static Concept concept(long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
    }
}
