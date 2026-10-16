package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationMappingMode;
import fr.siamois.domain.models.relation.RelationMirror;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationRuleKind;
import fr.siamois.domain.models.relation.RelationRuleSeverity;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.relation.RelationTypeMapping;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationLinkException.Reason;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkQueries;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationTypeMappingRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// lenient: the shared set-up stubs what each scenario only needs part of
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationLinkServiceTest {

    private static final String CODE = "TEST.ORDER";
    private static final Set<Long> REACH = Set.of(1L);

    @Mock RelationDefinitionRepository definitionRepository;
    @Mock RelationLinkRepository linkRepository;
    @Mock RelationLinkQueries linkQueries;
    @Mock RelationRuleRepository ruleRepository;
    @Mock RelationTypeMappingRepository mappingRepository;
    @Mock RelationEndpointResolver endpointResolver;
    @Mock RelationWriteAuthorizer authorizer;
    @Mock RelationLinkAttributesService attributesService;
    @Mock ConceptRepository conceptRepository;

    private RelationLinkService service;
    private RelationDefinition definition;
    private final PersonDTO caller = new PersonDTO();

    private final Concept fact = concept(10L);
    private final Concept sounding = concept(11L);
    private final Concept cuts = concept(20L);
    private final Concept cutBy = concept(21L);
    private final Concept covers = concept(22L);

    @BeforeEach
    void setUp() {
        service = new RelationLinkService(definitionRepository, linkRepository, linkQueries, ruleRepository,
                mappingRepository, endpointResolver, authorizer, attributesService, conceptRepository);
        caller.setId(5L);

        definition = new RelationDefinition();
        definition.setId(100L);
        definition.setCode(CODE);
        definition.setMirror(RelationMirror.OTHER);
        definition.addKind(RelationSide.SOURCE, RelationEntityKind.RECORDING_UNIT, null);
        // the target accepts two types of recording unit: a Fact or a Sounding
        definition.addKind(RelationSide.TARGET, RelationEntityKind.RECORDING_UNIT, fact);
        definition.addKind(RelationSide.TARGET, RelationEntityKind.RECORDING_UNIT, sounding);

        when(definitionRepository.findByCode(CODE)).thenReturn(Optional.of(definition));
        when(authorizer.canEdit(any(), any())).thenReturn(true);
        when(mappingRepository.findByDefinition(any())).thenReturn(List.of());
        when(ruleRepository.findByDefinitionsContaining(any())).thenReturn(List.of());
        when(linkQueries.linksBetween(any(), any(), anyLong(), any(), anyLong())).thenReturn(List.of());
        when(linkRepository.save(any(RelationLink.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Concept concept(long id) {
        Concept c = new Concept();
        c.setId(id);
        return c;
    }

    private RelationEndpoint unit(long id, Long projectId, Concept type) {
        RecordingUnit unit = new RecordingUnit();
        unit.setId(id);
        RelationEndpoint endpoint = new RelationEndpoint(RelationEntityKind.RECORDING_UNIT, id, unit, projectId, type, 1L);
        when(endpointResolver.find(RelationEntityKind.RECORDING_UNIT, id)).thenReturn(Optional.of(endpoint));
        return endpoint;
    }

    private RelationLinkService.CreateRequest request(long sourceId, long targetId) {
        return new RelationLinkService.CreateRequest(CODE, RelationEntityKind.RECORDING_UNIT, sourceId,
                RelationEntityKind.RECORDING_UNIT, targetId, null, null, null, null);
    }

    private RelationLinkService.CreateRequest request(long sourceId, long targetId, Long sourceType, Long targetType,
                                                      RelationSide editedFrom) {
        return new RelationLinkService.CreateRequest(CODE, RelationEntityKind.RECORDING_UNIT, sourceId,
                RelationEntityKind.RECORDING_UNIT, targetId, sourceType, targetType, editedFrom, null);
    }

    private void assertRefused(Reason reason, RelationLinkService.CreateRequest request) {
        assertThatThrownBy(() -> service.create(request, caller, REACH))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> assertThat(e.getReason()).isEqualTo(reason));
        verify(linkRepository, never()).save(any(RelationLink.class));
    }

    @Test
    void create_storesTheLinkOnceOrientedFromSourceToTarget() {
        unit(1, 7L, null);
        unit(2, 7L, fact);

        RelationLinkService.Result result = service.create(request(1, 2), caller, REACH);

        assertThat(result.link().getDefinition()).isSameAs(definition);
        assertThat(result.link().sourceId()).contains(1L);
        assertThat(result.link().targetId()).contains(2L);
        assertThat(result.warnings()).isEmpty();
        verify(attributesService).save(result.link(), null, caller);
    }

    @Test
    void create_aSideAcceptsSeveralTypesOfTheSameKind() {
        unit(1, 7L, null);
        unit(2, 7L, sounding);

        assertThat(service.create(request(1, 2), caller, REACH).link().targetId()).contains(2L);
    }

    @Test
    void create_refusesATypeTheSideDoesNotAccept() {
        unit(1, 7L, null);
        unit(2, 7L, concept(99L));

        assertRefused(Reason.TYPE_NOT_ALLOWED, request(1, 2));
    }

    @Test
    void create_refusesAnEntityWithoutTypeWhenTheSideRestrictsTypes() {
        unit(1, 7L, null);
        unit(2, 7L, null);

        assertRefused(Reason.TYPE_NOT_ALLOWED, request(1, 2));
    }

    @Test
    void create_refusesAKindTheSideDoesNotAccept() {
        unit(1, 7L, null);
        RelationEndpoint specimen = new RelationEndpoint(RelationEntityKind.SPECIMEN, 9L, new fr.siamois.domain.models.specimen.Specimen(), 7L, null, 1L);
        when(endpointResolver.find(RelationEntityKind.SPECIMEN, 9L)).thenReturn(Optional.of(specimen));

        assertRefused(Reason.KIND_NOT_ALLOWED, new RelationLinkService.CreateRequest(CODE, RelationEntityKind.RECORDING_UNIT, 1L,
                RelationEntityKind.SPECIMEN, 9L, null, null, null, null));
    }

    @Test
    void create_refusesTwoEntitiesOfDifferentProjects() {
        unit(1, 7L, null);
        unit(2, 8L, fact);

        assertRefused(Reason.CROSS_PROJECT, request(1, 2));
    }

    @Test
    void create_aPlaceHasNoProjectAndNeverCrossesProjects() {
        unit(1, 7L, null);
        unit(2, null, fact);

        assertThat(service.create(request(1, 2), caller, REACH).link()).isNotNull();
    }

    @Test
    void create_refusesAnEntityOutOfTheCallersReach() {
        unit(1, 7L, null);
        RelationEndpoint foreign = new RelationEndpoint(RelationEntityKind.RECORDING_UNIT, 2L, new RecordingUnit(), 7L, fact, 99L);
        when(endpointResolver.find(RelationEntityKind.RECORDING_UNIT, 2L)).thenReturn(Optional.of(foreign));

        assertRefused(Reason.ENDPOINT_NOT_FOUND, request(1, 2));
    }

    @Test
    void create_refusesAnUnknownEntity() {
        unit(1, 7L, null);
        when(endpointResolver.find(RelationEntityKind.RECORDING_UNIT, 2L)).thenReturn(Optional.empty());

        assertRefused(Reason.ENDPOINT_NOT_FOUND, request(1, 2));
    }

    @Test
    void create_refusesWithoutTheRightToEditTheEditedSide() {
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(authorizer.canEdit(any(), any())).thenReturn(false);

        assertRefused(Reason.FORBIDDEN, request(1, 2));
    }

    @Test
    void create_theMirrorSideIsReadOnlyByDefault() {
        unit(1, 7L, null);
        unit(2, 7L, fact);

        assertRefused(Reason.MIRROR_READ_ONLY, request(1, 2, null, null, RelationSide.TARGET));
    }

    @Test
    void create_theMirrorSideCanBeEditedWhenTheDefinitionAllowsIt_andThenTheTargetsRightIsChecked() {
        definition.setMirrorEditable(true);
        RelationEndpoint source = unit(1, 7L, null);
        RelationEndpoint target = unit(2, 7L, fact);

        service.create(request(1, 2, null, null, RelationSide.TARGET), caller, REACH);

        verify(authorizer).canEdit(target, caller);
        verify(authorizer, never()).canEdit(source, caller);
    }

    @Test
    void create_refusesAnAlreadyExistingLink() {
        unit(1, 7L, null);
        unit(2, 7L, fact);
        RelationLink existing = new RelationLink();
        existing.attachSource(new RecordingUnit() {{ setId(1L); }});
        existing.attachTarget(new RecordingUnit() {{ setId(2L); }});
        when(linkQueries.linksBetween(eq(List.of(definition)), any(), anyLong(), any(), anyLong())).thenReturn(List.of(existing));

        assertRefused(Reason.DUPLICATE, request(1, 2));
    }

    @Test
    void create_refusesLinkingAnEntityToItself() {
        unit(1, 7L, fact);
        definition.addKind(RelationSide.SOURCE, RelationEntityKind.RECORDING_UNIT, null);

        assertRefused(Reason.RULE_VIOLATION, request(1, 1));
    }

    // ---- types ----

    private void orderMapping(RelationMappingMode mode) {
        RelationTypeMapping mapping = new RelationTypeMapping();
        mapping.setDefinition(definition);
        mapping.setSourceType(cuts);
        mapping.setTargetType(cutBy);
        mapping.setMode(mode);
        when(mappingRepository.findByDefinition(definition)).thenReturn(List.of(mapping));
    }

    @Test
    void create_aTypeChosenOnOneSideGivesTheOtherSidesWhenMissing() {
        orderMapping(RelationMappingMode.IMPOSED);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(conceptRepository.findById(20L)).thenReturn(Optional.of(cuts));

        RelationLink link = service.create(request(1, 2, 20L, null, null), caller, REACH).link();

        assertThat(link.getSourceType()).isSameAs(cuts);
        assertThat(link.getTargetType()).isSameAs(cutBy);
    }

    @Test
    void create_aTypeChosenOnTheTargetSideGivesTheSourceSides() {
        orderMapping(RelationMappingMode.IMPOSED);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(conceptRepository.findById(21L)).thenReturn(Optional.of(cutBy));

        RelationLink link = service.create(request(1, 2, null, 21L, null), caller, REACH).link();

        assertThat(link.getSourceType()).isSameAs(cuts);
    }

    @Test
    void create_anImposedCorrespondenceRefusesAnotherTargetType() {
        orderMapping(RelationMappingMode.IMPOSED);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(conceptRepository.findById(20L)).thenReturn(Optional.of(cuts));
        when(conceptRepository.findById(21L)).thenReturn(Optional.of(cutBy));
        when(conceptRepository.findById(22L)).thenReturn(Optional.of(covers));

        assertRefused(Reason.TYPE_MISMATCH, request(1, 2, 20L, 22L, null));
    }

    @Test
    void create_aSuggestedCorrespondenceLetsTheTypesDiverge() {
        orderMapping(RelationMappingMode.SUGGESTED);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(conceptRepository.findById(20L)).thenReturn(Optional.of(cuts));
        when(conceptRepository.findById(22L)).thenReturn(Optional.of(covers));

        RelationLink link = service.create(request(1, 2, 20L, 22L, null), caller, REACH).link();

        assertThat(link.getTargetType()).isSameAs(covers);
    }

    @Test
    void create_refusesATypeOutsideTheCorrespondence() {
        orderMapping(RelationMappingMode.IMPOSED);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(conceptRepository.findById(22L)).thenReturn(Optional.of(covers));

        assertRefused(Reason.TYPE_NOT_ALLOWED, request(1, 2, 22L, null, null));
    }

    @Test
    void create_aTypeIsOptional() {
        orderMapping(RelationMappingMode.IMPOSED);
        unit(1, 7L, null);
        unit(2, 7L, fact);

        RelationLink link = service.create(request(1, 2), caller, REACH).link();

        assertThat(link.getSourceType()).isNull();
        assertThat(link.getTargetType()).isNull();
    }

    // ---- symmetric relation ----

    @Test
    void create_aSymmetricRelationStoresTheLowerIdAsSource_swappingTheTypesWithTheEnds() {
        definition.setMirror(RelationMirror.SELF);
        definition.addKind(RelationSide.TARGET, RelationEntityKind.RECORDING_UNIT, null);
        unit(8, 7L, null);
        unit(3, 7L, null);
        when(conceptRepository.findById(20L)).thenReturn(Optional.of(cuts));
        when(conceptRepository.findById(22L)).thenReturn(Optional.of(covers));

        RelationLink link = service.create(request(8, 3, 20L, 22L, null), caller, REACH).link();

        assertThat(link.sourceId()).contains(3L);
        assertThat(link.targetId()).contains(8L);
        assertThat(link.getSourceType()).isSameAs(covers);
        assertThat(link.getTargetType()).isSameAs(cuts);
    }

    @Test
    void create_aSymmetricRelationIsEditableFromEitherEnd() {
        definition.setMirror(RelationMirror.SELF);
        definition.addKind(RelationSide.TARGET, RelationEntityKind.RECORDING_UNIT, null);
        unit(1, 7L, null);
        unit(2, 7L, null);

        assertThat(service.create(request(1, 2, null, null, RelationSide.TARGET), caller, REACH).link()).isNotNull();
    }

    // ---- rules ----

    private RelationRule rule(RelationRuleKind kind, RelationRuleSeverity severity, Integer max) {
        RelationRule rule = new RelationRule();
        rule.setKind(kind);
        rule.setSeverity(severity);
        rule.setMaxCount(max);
        rule.getDefinitions().add(definition);
        when(ruleRepository.findByDefinitionsContaining(definition)).thenReturn(List.of(rule));
        return rule;
    }

    @Test
    void create_aBlockingNoCycleRuleRefusesALinkClosingACycle() {
        rule(RelationRuleKind.NO_CYCLE, RelationRuleSeverity.BLOCKING, null);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        // 2 already reaches 1 through the existing links: 1 → 2 would close the loop
        when(linkQueries.reaches(anyCollection(), eq(RelationEntityKind.RECORDING_UNIT), eq(2L), eq(1L))).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(1, 2), caller, REACH))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.RULE_VIOLATION);
                    assertThat(e.getViolations()).extracting(RuleViolation::rule).containsExactly(RelationRuleKind.NO_CYCLE);
                });
        verify(linkRepository, never()).save(any(RelationLink.class));
    }

    @Test
    void create_noCycleLetsAnAcyclicLinkThrough() {
        rule(RelationRuleKind.NO_CYCLE, RelationRuleSeverity.BLOCKING, null);
        unit(1, 7L, null);
        unit(2, 7L, fact);

        assertThat(service.create(request(1, 2), caller, REACH).link()).isNotNull();
    }

    @Test
    void create_aPairCardinalityRuleCountsTheLinksOfEitherDirection() {
        rule(RelationRuleKind.PAIR_CARDINALITY, RelationRuleSeverity.BLOCKING, 1);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(linkQueries.linksBetween(anyCollection(), any(), anyLong(), any(), anyLong()))
                .thenReturn(List.of(new RelationLink()));

        assertRefused(Reason.RULE_VIOLATION, request(1, 2));
    }

    @Test
    void create_anEntityCardinalityRuleLimitsTheLinksOfTheSource() {
        rule(RelationRuleKind.ENTITY_CARDINALITY, RelationRuleSeverity.BLOCKING, 1);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(linkQueries.countAsSource(definition, RelationEntityKind.RECORDING_UNIT, 1L)).thenReturn(1L);

        assertRefused(Reason.RULE_VIOLATION, request(1, 2));
    }

    @Test
    void create_anExclusionRuleRefusesAPairAlreadyLinkedByTheOtherDefinition() {
        RelationDefinition synchronous = new RelationDefinition();
        synchronous.setId(101L);
        RelationRule exclusion = rule(RelationRuleKind.EXCLUSION, RelationRuleSeverity.BLOCKING, null);
        exclusion.getDefinitions().add(synchronous);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(linkQueries.linksBetween(eq(Set.of(synchronous)), any(), anyLong(), any(), anyLong()))
                .thenReturn(List.of(new RelationLink()));

        assertRefused(Reason.RULE_VIOLATION, request(1, 2));
    }

    @Test
    void create_aWarningRuleLetsTheLinkThroughAndIsReported() {
        rule(RelationRuleKind.PAIR_CARDINALITY, RelationRuleSeverity.WARNING, 1);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(linkQueries.linksBetween(anyCollection(), any(), anyLong(), any(), anyLong()))
                .thenReturn(List.of(new RelationLink()));

        RelationLinkService.Result result = service.create(request(1, 2), caller, REACH);

        assertThat(result.link()).isNotNull();
        assertThat(result.warnings()).extracting(RuleViolation::severity).containsExactly(RelationRuleSeverity.WARNING);
    }

    @Test
    void create_anUnknownDefinitionIsRefused() {
        when(definitionRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new RelationLinkService.CreateRequest("NOPE", RelationEntityKind.RECORDING_UNIT, 1L,
                RelationEntityKind.RECORDING_UNIT, 2L, null, null, null, null), caller, REACH))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.DEFINITION_NOT_FOUND));
    }

    // ---- attributes, update, delete ----

    @Test
    void create_savesTheLinksAttributesWithTheCaller() {
        unit(1, 7L, null);
        unit(2, 7L, fact);
        Map<Long, Object> attributes = Map.of(-900L, true);

        RelationLink link = service.create(new RelationLinkService.CreateRequest(CODE, RelationEntityKind.RECORDING_UNIT, 1L,
                RelationEntityKind.RECORDING_UNIT, 2L, null, null, null, attributes), caller, REACH).link();

        verify(attributesService).save(link, attributes, caller);
    }

    private RelationLink existingLink(long id) {
        RelationLink link = new RelationLink();
        link.setId(id);
        link.setDefinition(definition);
        RecordingUnit source = new RecordingUnit();
        source.setId(1L);
        RecordingUnit target = new RecordingUnit();
        target.setId(2L);
        link.attachSource(source);
        link.attachTarget(target);
        when(linkRepository.findById(id)).thenReturn(Optional.of(link));
        return link;
    }

    @Test
    void update_changesTheTypesAndTheAttributes() {
        orderMapping(RelationMappingMode.IMPOSED);
        RelationLink link = existingLink(50L);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(conceptRepository.findById(20L)).thenReturn(Optional.of(cuts));
        Map<Long, Object> attributes = Map.of(-900L, false);

        service.update(50L, new RelationLinkService.UpdateRequest(true, 20L, null, null, attributes), caller, REACH);

        assertThat(link.getSourceType()).isSameAs(cuts);
        assertThat(link.getTargetType()).isSameAs(cutBy);
        verify(attributesService).save(link, attributes, caller);
    }

    @Test
    void update_withoutChangeTypesLeavesTheTypesAlone() {
        RelationLink link = existingLink(50L);
        link.setSourceType(cuts);
        unit(1, 7L, null);
        unit(2, 7L, fact);

        service.update(50L, new RelationLinkService.UpdateRequest(false, null, null, null, Map.of()), caller, REACH);

        assertThat(link.getSourceType()).isSameAs(cuts);
    }

    @Test
    void delete_removesTheLinkAndItsAnswers() {
        RelationLink link = existingLink(50L);
        unit(1, 7L, null);
        unit(2, 7L, fact);

        service.delete(50L, null, caller, REACH);

        verify(attributesService).deleteAll(link);
        verify(linkRepository).delete(link);
    }

    @Test
    void delete_refusedFromTheReadOnlyMirror() {
        existingLink(50L);
        unit(1, 7L, null);
        unit(2, 7L, fact);

        assertThatThrownBy(() -> service.delete(50L, RelationSide.TARGET, caller, REACH))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.MIRROR_READ_ONLY));
        verify(linkRepository, never()).delete(any(RelationLink.class));
    }

    @Test
    void delete_refusedWithoutTheRight() {
        existingLink(50L);
        unit(1, 7L, null);
        unit(2, 7L, fact);
        when(authorizer.canEdit(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.delete(50L, null, caller, REACH))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.FORBIDDEN));
    }

    @Test
    void delete_anUnknownLinkIsRefused() {
        when(linkRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L, null, caller, REACH))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> assertThat(e.getReason()).isEqualTo(Reason.LINK_NOT_FOUND));
    }

    // ---- audit ----

    @Test
    void audit_flagsALinkWhoseEndNoLongerHasAnAcceptedType_withoutTouchingIt() {
        RelationLink link = existingLink(50L);
        unit(1, 7L, null);
        unit(2, 7L, concept(99L)); // the entity's type changed after the link was made

        List<String> problems = service.audit(link);

        assertThat(problems).hasSize(1);
        verify(linkRepository, never()).delete(any(RelationLink.class));
    }

    @Test
    void audit_aConformingLinkHasNoProblem() {
        RelationLink link = existingLink(50L);
        unit(1, 7L, null);
        unit(2, 7L, fact);

        assertThat(service.audit(link)).isEmpty();
    }

    @Test
    void create_capturesTheOrientationOfTheStoredLink() {
        unit(1, 7L, null);
        unit(2, 7L, fact);

        service.create(request(1, 2), caller, REACH);

        ArgumentCaptor<RelationLink> saved = ArgumentCaptor.forClass(RelationLink.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().sourceKind()).contains(RelationEntityKind.RECORDING_UNIT);
    }
}
