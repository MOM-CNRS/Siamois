package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.relation.SystemRelations;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationLinkAttributesService;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordingUnitStratiRelSeederTest {

    @Mock RelationDefinitionRepository relationDefinitionRepository;
    @Mock RelationLinkRepository relationLinkRepository;
    @Mock RelationLinkAttributesService relationLinkAttributesService;
    @Mock RecordingUnitSeeder recordingUnitSeeder;
    @Mock ConceptRepository conceptRepository;
    @Mock ConceptSeeder conceptSeeder;
    @Mock EntityManager entityManager;
    @Mock Query existingPairsQuery;

    @InjectMocks
    RecordingUnitStratiRelSeeder seeder;

    private static final String VOCABULARY_ID = "th240";
    private static final Long INSTITUTION_ID = 1L;
    private static final String ACTION_UNIT_ID = "action-01";

    private final RelationDefinition order = definition(SystemRelations.ORDER_CODE);
    private final RelationDefinition synchronous = definition(SystemRelations.SYNCHRONOUS_CODE);
    private final Concept concept = new Concept();
    private final Person author = new Person();

    private static RelationDefinition definition(String code) {
        RelationDefinition definition = new RelationDefinition();
        definition.setCode(code);
        return definition;
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seeder, "entityManager", entityManager);
        when(relationDefinitionRepository.findByCode(SystemRelations.ORDER_CODE)).thenReturn(Optional.of(order));
        when(relationDefinitionRepository.findByCode(SystemRelations.SYNCHRONOUS_CODE)).thenReturn(Optional.of(synchronous));
        when(entityManager.createQuery(anyString())).thenReturn(existingPairsQuery);
        when(existingPairsQuery.setParameter(anyString(), any())).thenReturn(existingPairsQuery);
        when(existingPairsQuery.getResultList()).thenReturn(List.of());
        concept.setExternalId("4287541");
    }

    private RecordingUnit unit(String fullIdentifier, long id) {
        RecordingUnit ru = new RecordingUnit();
        ru.setFullIdentifier(fullIdentifier);
        ru.setId(id);
        ru.setCreatedBy(author);
        return ru;
    }

    private void stubRecordingUnits(RecordingUnit... units) {
        Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> byKey = new HashMap<>();
        for (RecordingUnit u : units) {
            byKey.put(new RecordingUnitSeeder.RecordingUnitKey(u.getFullIdentifier(), ACTION_UNIT_ID), u);
        }
        when(recordingUnitSeeder.bulkGetRecordingUnitsFromKeys(anyCollection(), eq(INSTITUTION_ID))).thenReturn(byKey);
    }

    private void stubConceptFound() {
        when(conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(eq(VOCABULARY_ID), anyCollection()))
                .thenReturn(List.of(concept));
    }

    private static RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO spec(String us1, String us2, ConceptSeeder.ConceptKey rel,
                                                                                boolean direction, boolean asynchronous, boolean uncertain) {
        return new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(us1, us2, rel, direction, asynchronous, uncertain, null);
    }

    private List<RelationLink> savedLinks() {
        ArgumentCaptor<Iterable<RelationLink>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(relationLinkRepository).saveAll(captor.capture());
        List<RelationLink> links = new java.util.ArrayList<>();
        captor.getValue().forEach(links::add);
        return links;
    }

    @Test
    void seed_us1DoesNotExist_throws() {
        stubRecordingUnits(unit("US2", 2L));
        // "US1" isn't in the bulk lookup result -> missing

        var specs = List.of(spec("US1", "US2", null, false, false, false));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(specs, INSTITUTION_ID, ACTION_UNIT_ID));

        assertThat(ex.getMessage()).contains("[Relation strati ligne 1]").contains("Recording unit introuvable");
    }

    @Test
    void seed_theRelationsOfThePlatformMustHaveBeenInitialized() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));
        when(relationDefinitionRepository.findByCode(SystemRelations.ORDER_CODE)).thenReturn(Optional.empty());

        var specs = List.of(spec("US1", "US2", null, false, true, false));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(specs, INSTITUTION_ID, ACTION_UNIT_ID));

        assertThat(ex.getMessage()).contains(SystemRelations.ORDER_CODE);
    }

    @Test
    void seed_conceptNotFound_throws() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));
        // conceptRepository left unstubbed -> empty -> concept not found
        when(conceptSeeder.describeMissingConcept(any(), any())).thenReturn("Concept non chargé dans Siamois (test)");

        var specs = List.of(spec("US1", "US2", new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"), false, false, false));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(specs, INSTITUTION_ID, ACTION_UNIT_ID));

        assertThat(ex.getMessage()).contains("Concept non chargé dans Siamois");
    }

    @Test
    void seed_aRowWithoutTypeIsStillALink() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));

        seeder.seed(List.of(spec("US1", "US2", null, false, true, false)), INSTITUTION_ID, ACTION_UNIT_ID);

        RelationLink link = savedLinks().get(0);
        assertThat(link.getSourceType()).isNull();
        assertThat(link.getTargetType()).isNull();
    }

    @Test
    void seed_alreadyExists_saveNeverCalled() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));
        stubConceptFound();
        // the order link US2 (posterior, source) -> US1 (anterior, target) is already stored
        when(existingPairsQuery.getResultList()).thenReturn(List.<Object>of((Object) new Object[]{SystemRelations.ORDER_CODE, 2L, 1L}));

        seeder.seed(List.of(spec("US1", "US2", new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"), false, true, false)),
                INSTITUTION_ID, ACTION_UNIT_ID);

        verify(relationLinkRepository, never()).saveAll(any());
    }

    @Test
    void seed_anAsynchronousRowIsAnOrderLink_theLaterUnitIsTheSource() {
        RecordingUnit us1 = unit("US1", 1L);
        RecordingUnit us2 = unit("US2", 2L);
        stubRecordingUnits(us1, us2);

        seeder.seed(List.of(spec("US1", "US2", null, false, true, false)), INSTITUTION_ID, ACTION_UNIT_ID);

        RelationLink link = savedLinks().get(0);
        assertThat(link.getDefinition()).isSameAs(order);
        assertThat(link.getSourceRecordingUnit()).isSameAs(us2);
        assertThat(link.getTargetRecordingUnit()).isSameAs(us1);
        verify(entityManager, times(2)).flush();
        verify(entityManager, times(1)).clear();
    }

    @Test
    void seed_theConceptIsTheTypeOfTheEndItReadsFrom() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));
        stubConceptFound();
        var key = new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541");

        seeder.seed(List.of(spec("US1", "US2", key, true, true, false)), INSTITUTION_ID, ACTION_UNIT_ID);
        RelationLink readsFromUs1 = savedLinks().get(0);
        assertThat(readsFromUs1.getTargetType()).isSameAs(concept);
        assertThat(readsFromUs1.getSourceType()).isNull();

        reset(relationLinkRepository);
        seeder.seed(List.of(spec("US1", "US2", key, false, true, false)), INSTITUTION_ID, ACTION_UNIT_ID);
        RelationLink readsFromUs2 = savedLinks().get(0);
        assertThat(readsFromUs2.getSourceType()).isSameAs(concept);
        assertThat(readsFromUs2.getTargetType()).isNull();
    }

    @Test
    void seed_aSynchronousRowIsASymmetricLink_theLowerIdIsTheSource() {
        RecordingUnit us1 = unit("US1", 9L);
        RecordingUnit us2 = unit("US2", 3L);
        stubRecordingUnits(us1, us2);

        seeder.seed(List.of(spec("US1", "US2", null, false, false, false)), INSTITUTION_ID, ACTION_UNIT_ID);

        RelationLink link = savedLinks().get(0);
        assertThat(link.getDefinition()).isSameAs(synchronous);
        assertThat(link.getSourceRecordingUnit()).isSameAs(us2);
        assertThat(link.getTargetRecordingUnit()).isSameAs(us1);
    }

    @Test
    void seed_uncertainIsTheLinksOwnAttribute_writtenOnBehalfOfTheUnitsCreator() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));
        when(relationLinkRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed(List.of(spec("US1", "US2", null, false, true, true), spec("US1", "US2", null, false, false, false)),
                INSTITUTION_ID, ACTION_UNIT_ID);

        verify(relationLinkAttributesService, times(1)).save(any(RelationLink.class),
                eq(Map.of(SystemRelations.UNCERTAIN_FIELD_ID, true)), eq(author));
    }

    @Test
    void seed_aRepeatedRowInTheSameImportIsOneLink() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));

        seeder.seed(List.of(spec("US1", "US2", null, false, true, false), spec("US1", "US2", null, false, true, false)),
                INSTITUTION_ID, ACTION_UNIT_ID);

        assertThat(savedLinks().size()).isEqualTo(1);
    }
}
