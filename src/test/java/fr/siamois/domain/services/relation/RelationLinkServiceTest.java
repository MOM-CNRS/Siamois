package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.RelationSide;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationLinkException.Reason;
import fr.siamois.domain.services.relation.RelationLinkService.CreateRequest;
import fr.siamois.domain.services.relation.RelationLinkService.End;
import fr.siamois.domain.services.relation.RelationLinkService.UpdateRequest;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkQueries;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationRuleExclusionRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RelationLinkServiceTest {

    private static final long PROJECT = 7L;
    private static final long INSTITUTION = 3L;
    private static final Set<Long> REACH = Set.of(INSTITUTION);

    private final RelationDefinitionRepository definitionRepository = mock(RelationDefinitionRepository.class);
    private final RelationLinkRepository linkRepository = mock(RelationLinkRepository.class);
    private final RelationLinkQueries linkQueries = mock(RelationLinkQueries.class);
    private final RelationRuleExclusionRepository exclusionRepository = mock(RelationRuleExclusionRepository.class);
    private final RelationRuleResolver ruleResolver = mock(RelationRuleResolver.class);
    private final RelationEndpointResolver endpointResolver = mock(RelationEndpointResolver.class);
    private final RelationWriteAuthorizer authorizer = mock(RelationWriteAuthorizer.class);
    private final ConceptRepository conceptRepository = mock(ConceptRepository.class);

    private final RelationLinkService service = new RelationLinkService(definitionRepository, linkRepository,
            linkQueries, exclusionRepository, ruleResolver, endpointResolver, authorizer, conceptRepository);

    private final PersonDTO caller = new PersonDTO();
    private RelationDefinition order;
    private RelationEndpoint us12;
    private RelationEndpoint us5;

    @BeforeEach
    void setUp() {
        caller.setId(1L);
        order = definition(10L, RelationKind.COMPLEX, false);
        us12 = endpoint(RelationEndKind.RECORDING_UNIT, 12L, PROJECT, 100L);
        us5 = endpoint(RelationEndKind.RECORDING_UNIT, 5L, PROJECT, 100L);
        when(authorizer.canEdit(any(), eq(caller))).thenReturn(true);
        when(linkRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(linkRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(conceptRepository.findById(anyLong())).thenAnswer(i -> Optional.of(concept(i.getArgument(0))));
        when(exclusionRepository.findDefinitionsExcluding(anyLong())).thenReturn(List.of());
        rules(order, RelationRuleSet.none(order));
    }

    @Test
    void storesALinkEnteredFromTheSourceField() {
        RelationLinkService.Result result = service.create(new CreateRequest(10L, RelationSide.SOURCE,
                end(us12), end(us5), 201L, false), caller, REACH);

        RelationLink link = result.link();
        assertThat(link.getSourceId()).isEqualTo(12L);
        assertThat(link.getTargetId()).isEqualTo(5L);
        assertThat(link.getProjectId()).isEqualTo(PROJECT);
        assertThat(link.getQualifier().getId()).isEqualTo(201L);
        assertThat(link.isSimple()).isFalse();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void aLinkEnteredFromTheMirrorFieldIsStoredFromTheSourceWithTheQualifierItMirrors() {
        // US 5, "Antérieur à": "est recouverte par US 12" is stored as US 12 "recouvre" US 5.
        rules(order, rules(order, false, false, false, List.of(), Map.of(201L, 301L), List.of()));

        RelationLink link = service.create(new CreateRequest(10L, RelationSide.TARGET, end(us5), end(us12),
                301L, false), caller, REACH).link();

        assertThat(link.getSourceId()).isEqualTo(12L);
        assertThat(link.getTargetId()).isEqualTo(5L);
        assertThat(link.getQualifier().getId()).isEqualTo(201L);
        verify(authorizer).canEdit(us5, caller);
        verify(authorizer).canEdit(us12, caller);
    }

    @Test
    void aSymmetricPairIsStoredInOneOrder() {
        RelationDefinition synchrony = definition(11L, RelationKind.SIMPLE, true);
        rules(synchrony, RelationRuleSet.none(synchrony));

        RelationLink link = service.create(new CreateRequest(11L, RelationSide.SOURCE, end(us12), end(us5),
                null, false), caller, REACH).link();

        assertThat(link.getSourceId()).isEqualTo(5L);
        assertThat(link.getTargetId()).isEqualTo(12L);
        assertThat(link.isSimple()).isTrue();
    }

    @Test
    void refusesALinkFromARecordToItself() {
        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us12), null, false), Reason.SELF_LINK);
    }

    @Test
    void refusesTwoRecordsOfDifferentProjects() {
        RelationEndpoint elsewhere = endpoint(RelationEndKind.RECORDING_UNIT, 8L, 99L, 100L);

        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(elsewhere), null, false),
                Reason.CROSS_PROJECT);
    }

    @Test
    void refusesARecordOutOfTheCallersReach() {
        assertThatThrownBy(() -> service.create(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us5),
                null, false), caller, Set.of(999L)))
                .isInstanceOfSatisfying(RelationLinkException.class,
                        e -> assertThat(e.getReason()).isEqualTo(Reason.ENDPOINT_NOT_FOUND));
    }

    @Test
    void refusesWithoutTheRightToEditTheRecord() {
        when(authorizer.canEdit(us12, caller)).thenReturn(false);

        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us5), null, false), Reason.FORBIDDEN);
    }

    @Test
    void theMirrorFieldNeedsTheRightToEditBothRecords() {
        when(authorizer.canEdit(us12, caller)).thenReturn(false);

        assertRefused(new CreateRequest(10L, RelationSide.TARGET, end(us5), end(us12), null, false), Reason.FORBIDDEN);
    }

    @Test
    void refusesAKindOrATypeTheSideDoesNotAccept() {
        RelationEndpoint find = endpoint(RelationEndKind.SPECIMEN, 40L, PROJECT, 100L);
        rules(order, rules(order, false, false, false, List.of(
                new RelationRuleSet.AcceptedKind(RelationSide.TARGET, RelationEndKind.RECORDING_UNIT, 100L)),
                Map.of(), List.of()));

        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(find), null, false),
                Reason.KIND_NOT_ALLOWED);
        RelationEndpoint fact = endpoint(RelationEndKind.RECORDING_UNIT, 6L, PROJECT, 101L);
        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(fact), null, false),
                Reason.TYPE_NOT_ALLOWED);
    }

    @Test
    void aSimpleRelationHasNoQualifier() {
        RelationDefinition inclusion = definition(12L, RelationKind.SIMPLE, false);
        rules(inclusion, RelationRuleSet.none(inclusion));

        assertRefused(new CreateRequest(12L, RelationSide.SOURCE, end(us12), end(us5), 201L, false),
                Reason.INVALID_QUALIFIER);
    }

    @Test
    void refusesASimpleLinkStoredAlready() {
        RelationDefinition inclusion = definition(12L, RelationKind.SIMPLE, false);
        rules(inclusion, RelationRuleSet.none(inclusion));
        when(linkQueries.linked(12L, RelationEndKind.RECORDING_UNIT, 12L, RelationEndKind.RECORDING_UNIT, 5L))
                .thenReturn(true);

        assertRefused(new CreateRequest(12L, RelationSide.SOURCE, end(us12), end(us5), null, false), Reason.DUPLICATE);
    }

    @Test
    void aDuplicateWrittenConcurrentlyIsReportedAsADuplicate() {
        when(linkRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_relation_link_simple"));

        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us5), null, false), Reason.DUPLICATE);
    }

    @Test
    void aSingleChoiceSideRefusesASecondLink() {
        rules(order, rules(order, false, true, false, List.of(), Map.of(), List.of()));
        when(linkQueries.countAt(10L, RelationSide.SOURCE, false, RelationEndKind.RECORDING_UNIT, 12L)).thenReturn(1L);

        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us5), null, false),
                Reason.RULE_VIOLATION);
        verify(linkQueries).lockProject(PROJECT);
    }

    @Test
    void refusesAPairAlreadyLinkedByAnExclusiveRelation() {
        rules(order, rules(order, false, false, false, List.of(), Map.of(), List.of(11L)));
        when(linkQueries.linkedEitherWay(Set.of(11L), RelationEndKind.RECORDING_UNIT, 12L,
                RelationEndKind.RECORDING_UNIT, 5L)).thenReturn(true);

        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us5), null, false),
                Reason.RULE_VIOLATION);
    }

    @Test
    void anExclusionDeclaredByTheOtherRelationOnlyStillHolds() {
        RelationDefinition synchrony = definition(11L, RelationKind.SIMPLE, true);
        rules(synchrony, rules(synchrony, false, false, false, List.of(), Map.of(), List.of(10L)));
        when(exclusionRepository.findDefinitionsExcluding(10L)).thenReturn(List.of(11L));
        when(linkQueries.linkedEitherWay(Set.of(11L), RelationEndKind.RECORDING_UNIT, 12L,
                RelationEndKind.RECORDING_UNIT, 5L)).thenReturn(true);

        assertRefused(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us5), null, false),
                Reason.RULE_VIOLATION);
    }

    @Test
    void refusesACycleOfCertainLinksWithItsPath() {
        rules(order, rules(order, true, false, false, List.of(), Map.of(), List.of()));
        // US 5 already leads to US 12 through link 900.
        when(linkQueries.reaches(10L, RelationEndKind.RECORDING_UNIT, 5L, RelationEndKind.RECORDING_UNIT, 12L, true))
                .thenReturn(true);
        when(linkRepository.findOfProjects(10L, List.of(PROJECT))).thenReturn(List.of(
                stored(900L, 5L, 12L, false)));

        assertThatThrownBy(() -> service.create(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us5),
                null, false), caller, REACH))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> {
                    assertThat(e.getReason()).isEqualTo(Reason.RULE_VIOLATION);
                    assertThat(e.getViolations()).singleElement().satisfies(v -> {
                        assertThat(v.rule()).isEqualTo(RuleViolation.Rule.NO_CYCLE);
                        assertThat(v.linkIds()).containsExactly(900L);
                    });
                });
    }

    @Test
    void aCycleThroughAnUncertainLinkIsOnlyAWarning() {
        rules(order, rules(order, true, false, false, List.of(), Map.of(), List.of()));
        when(linkQueries.reaches(10L, RelationEndKind.RECORDING_UNIT, 5L, RelationEndKind.RECORDING_UNIT, 12L, false))
                .thenReturn(true);
        when(linkRepository.findOfProjects(10L, List.of(PROJECT))).thenReturn(List.of(stored(900L, 5L, 12L, true)));

        RelationLinkService.Result result = service.create(new CreateRequest(10L, RelationSide.SOURCE, end(us12),
                end(us5), null, false), caller, REACH);

        assertThat(result.warnings()).singleElement().satisfies(v -> {
            assertThat(v.blocking()).isFalse();
            assertThat(v.linkIds()).containsExactly(900L);
        });
    }

    @Test
    void anUncertainNewLinkClosingACycleIsOnlyAWarning() {
        rules(order, rules(order, true, false, false, List.of(), Map.of(), List.of()));
        when(linkQueries.reaches(eq(10L), any(), eq(5L), any(), eq(12L), anyBoolean())).thenReturn(true);

        RelationLinkService.Result result = service.create(new CreateRequest(10L, RelationSide.SOURCE, end(us12),
                end(us5), null, true), caller, REACH);

        assertThat(result.warnings()).hasSize(1);
        assertThat(result.link().isUncertain()).isTrue();
    }

    @Test
    void writesWithoutARuleThatReadsOtherLinksTakeNoLock() {
        service.create(new CreateRequest(10L, RelationSide.SOURCE, end(us12), end(us5), null, false), caller, REACH);

        verify(linkQueries, never()).lockProject(anyLong());
    }

    @Test
    void makingAnUncertainLinkCertainRefusesTheCycleItCloses() {
        rules(order, rules(order, true, false, false, List.of(), Map.of(), List.of()));
        RelationLink link = stored(901L, 12L, 5L, true);
        when(linkRepository.findById(901L)).thenReturn(Optional.of(link));
        when(linkQueries.reaches(10L, RelationEndKind.RECORDING_UNIT, 5L, RelationEndKind.RECORDING_UNIT, 12L, true))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(901L, new UpdateRequest(RelationSide.SOURCE, null, false), caller,
                REACH)).isInstanceOfSatisfying(RelationLinkException.class,
                e -> assertThat(e.getReason()).isEqualTo(Reason.RULE_VIOLATION));
        verify(linkQueries).lockProject(PROJECT);
    }

    @Test
    void updatingTheQualifierFromTheMirrorFieldStoresTheQualifierItMirrors() {
        rules(order, rules(order, false, false, false, List.of(), Map.of(201L, 301L), List.of()));
        RelationLink link = stored(901L, 12L, 5L, false);
        when(linkRepository.findById(901L)).thenReturn(Optional.of(link));

        service.update(901L, new UpdateRequest(RelationSide.TARGET, 301L, false), caller, REACH);

        ArgumentCaptor<RelationLink> saved = ArgumentCaptor.forClass(RelationLink.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().getQualifier().getId()).isEqualTo(201L);
    }

    @Test
    void deletingASymmetricLinkTakesTheRightToEditEitherRecord() {
        RelationDefinition synchrony = definition(11L, RelationKind.SIMPLE, true);
        RelationLink link = stored(902L, 5L, 12L, false);
        link.setDefinition(synchrony);
        when(linkRepository.findById(902L)).thenReturn(Optional.of(link));
        when(authorizer.canEdit(us5, caller)).thenReturn(false);

        service.delete(902L, RelationSide.SOURCE, caller, REACH);

        verify(linkRepository).delete(link);
    }

    // ---- helpers ----

    private void assertRefused(CreateRequest request, Reason reason) {
        assertThatThrownBy(() -> service.create(request, caller, REACH))
                .isInstanceOfSatisfying(RelationLinkException.class, e -> assertThat(e.getReason()).isEqualTo(reason));
    }

    private RelationDefinition definition(long id, RelationKind kind, boolean symmetric) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(id);
        definition.setKind(kind);
        definition.setSymmetric(symmetric);
        when(definitionRepository.findById(id)).thenReturn(Optional.of(definition));
        return definition;
    }

    private void rules(RelationDefinition definition, RelationRuleSet rules) {
        when(ruleResolver.rulesFor(definition, PROJECT)).thenReturn(rules);
    }

    private static RelationRuleSet rules(RelationDefinition definition, boolean noCycle, boolean sourceUnique,
                                         boolean targetUnique, List<RelationRuleSet.AcceptedKind> kinds,
                                         Map<Long, Long> mirrors, List<Long> excluded) {
        return new RelationRuleSet(definition, null, noCycle, sourceUnique, targetUnique, kinds, mirrors, excluded);
    }

    private RelationEndpoint endpoint(RelationEndKind kind, long id, long project, long typeId) {
        RelationEndpoint endpoint = new RelationEndpoint(kind, id, new Object(), project, concept(typeId), INSTITUTION);
        when(endpointResolver.find(kind, id)).thenReturn(Optional.of(endpoint));
        return endpoint;
    }

    private static End end(RelationEndpoint endpoint) {
        return new End(endpoint.kind(), endpoint.id());
    }

    private RelationLink stored(long id, long sourceId, long targetId, boolean uncertain) {
        RelationLink link = new RelationLink();
        link.setId(id);
        link.setDefinition(order);
        link.setProjectId(PROJECT);
        link.setSourceKind(RelationEndKind.RECORDING_UNIT);
        link.setSourceId(sourceId);
        link.setTargetKind(RelationEndKind.RECORDING_UNIT);
        link.setTargetId(targetId);
        link.setUncertain(uncertain);
        return link;
    }

    private static Concept concept(long id) {
        Concept concept = new Concept();
        concept.setId(id);
        return concept;
    }
}
