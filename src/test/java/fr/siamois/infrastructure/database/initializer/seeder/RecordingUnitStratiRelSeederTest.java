package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationKind;
import fr.siamois.domain.models.relation.RelationLink;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.relation.RelationRuleResolver;
import fr.siamois.domain.services.relation.RelationRuleSet;
import fr.siamois.infrastructure.database.repositories.relation.RelationDefinitionRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationLinkRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** The import of a stratigraphy sheet, as links of the stratigraphic definitions (docs/reprise-stratigraphie.md). */
class RecordingUnitStratiRelSeederTest {

    private static final String VOCABULARY_ID = "th240";
    private static final Long INSTITUTION_ID = 1L;
    private static final String ACTION_UNIT_ID = "action-01";
    private static final long PROJECT = 7L;

    private final RelationLinkRepository linkRepository = mock(RelationLinkRepository.class);
    private final RelationDefinitionRepository definitionRepository = mock(RelationDefinitionRepository.class);
    private final RelationRuleResolver ruleResolver = mock(RelationRuleResolver.class);
    private final RecordingUnitSeeder recordingUnitSeeder = mock(RecordingUnitSeeder.class);
    private final ConceptRepository conceptRepository = mock(ConceptRepository.class);
    private final ConceptSeeder conceptSeeder = mock(ConceptSeeder.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    private final RecordingUnitStratiRelSeeder seeder = new RecordingUnitStratiRelSeeder(linkRepository,
            definitionRepository, ruleResolver, recordingUnitSeeder, conceptRepository, conceptSeeder);

    private RelationDefinition order;
    private RelationDefinition synchrony;
    private Concept recouvre;
    private Concept recouverte;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seeder, "entityManager", entityManager);
        order = definition(10L, false);
        synchrony = definition(11L, true);
        when(definitionRepository.findByField(-328L)).thenReturn(Optional.of(order));
        when(definitionRepository.findByField(-330L)).thenReturn(Optional.of(synchrony));
        recouvre = concept(201L, "recouvre");
        recouverte = concept(301L, "est-recouverte");
        when(ruleResolver.rulesFor(order, PROJECT)).thenReturn(new RelationRuleSet(order, null, true, false, false,
                List.of(), Map.of(201L, 301L), List.of()));
        when(conceptRepository.findById(201L)).thenReturn(Optional.of(recouvre));
        when(conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(eq(VOCABULARY_ID), anyCollection()))
                .thenReturn(List.of(recouvre, recouverte));
    }

    @Test
    void anOrderRelationGoesFromThePosteriorUnitWithTheQualifierItReads() {
        stubUnits(unit("US1", 1L, PROJECT), unit("US2", 2L, PROJECT));

        // The file reads "US1 est recouverte par US2" from US1: the link stores US2 recouvre US1.
        seeder.seed(List.of(spec("est-recouverte", true, true, false)), INSTITUTION_ID, ACTION_UNIT_ID);

        RelationLink link = onlySaved();
        assertThat(link.getDefinition()).isSameAs(order);
        assertThat(link.getSourceId()).isEqualTo(2L);
        assertThat(link.getTargetId()).isEqualTo(1L);
        assertThat(link.getQualifier()).isSameAs(recouvre);
        assertThat(link.getProjectId()).isEqualTo(PROJECT);
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void aSynchronousRelationStoresItsPairInOneOrderAndItsUncertainty() {
        stubUnits(unit("US1", 5L, PROJECT), unit("US2", 3L, PROJECT));

        seeder.seed(List.of(spec(null, null, false, true)), INSTITUTION_ID, ACTION_UNIT_ID);

        RelationLink link = onlySaved();
        assertThat(link.getDefinition()).isSameAs(synchrony);
        assertThat(link.getSourceId()).isEqualTo(3L);
        assertThat(link.getTargetId()).isEqualTo(5L);
        assertThat(link.getQualifier()).isNull();
        assertThat(link.isUncertain()).isTrue();
    }

    @Test
    void aRelationAlreadyStoredIsSkipped() {
        stubUnits(unit("US1", 1L, PROJECT), unit("US2", 2L, PROJECT));
        RelationLink existing = new RelationLink();
        existing.setDefinition(order);
        existing.setSourceId(2L);
        existing.setTargetId(1L);
        when(linkRepository.findStratigraphicFromSources(anyCollection())).thenReturn(List.of(existing));

        seeder.seed(List.of(spec("recouvre", false, true, false)), INSTITUTION_ID, ACTION_UNIT_ID);

        verify(linkRepository, never()).saveAll(any());
    }

    @Test
    void aMissingUnitOrTwoProjectsAreRefusedWithTheLine() {
        stubUnits(unit("US2", 2L, PROJECT));
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> seeder.seed(List.of(spec(null, null, true, false)), INSTITUTION_ID, ACTION_UNIT_ID));
        assertThat(missing.getMessage()).contains("[Relation strati ligne 1]").contains("Recording unit introuvable");

        stubUnits(unit("US1", 1L, PROJECT), unit("US2", 2L, 99L));
        IllegalStateException crossProject = assertThrows(IllegalStateException.class,
                () -> seeder.seed(List.of(spec(null, null, true, false)), INSTITUTION_ID, ACTION_UNIT_ID));
        assertThat(crossProject.getMessage()).contains("même projet");
    }

    @Test
    void anUnknownConceptIsRefused() {
        stubUnits(unit("US1", 1L, PROJECT), unit("US2", 2L, PROJECT));
        when(conceptSeeder.describeMissingConcept(any(), any())).thenReturn("Concept non chargé dans Siamois (test)");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(List.of(spec("inconnu", false, true, false)), INSTITUTION_ID, ACTION_UNIT_ID));

        assertThat(ex.getMessage()).contains("Concept non chargé dans Siamois");
    }

    private RelationLink onlySaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RelationLink>> saved = ArgumentCaptor.forClass(List.class);
        verify(linkRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        return saved.getValue().get(0);
    }

    private static RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO spec(String concept, Boolean direction,
                                                                               boolean asynchronous, boolean uncertain) {
        return new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO("US1", "US2",
                concept == null ? null : new ConceptSeeder.ConceptKey(VOCABULARY_ID, concept), direction, asynchronous,
                uncertain, null);
    }

    private void stubUnits(RecordingUnit... units) {
        Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> byKey = new HashMap<>();
        for (RecordingUnit u : units) {
            byKey.put(new RecordingUnitSeeder.RecordingUnitKey(u.getFullIdentifier(), ACTION_UNIT_ID), u);
        }
        when(recordingUnitSeeder.bulkGetRecordingUnitsFromKeys(anyCollection(), eq(INSTITUTION_ID))).thenReturn(byKey);
    }

    private static RecordingUnit unit(String fullIdentifier, long id, long project) {
        RecordingUnit ru = new RecordingUnit();
        ru.setFullIdentifier(fullIdentifier);
        ru.setId(id);
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(project);
        ru.setActionUnit(actionUnit);
        return ru;
    }

    private static RelationDefinition definition(long id, boolean symmetric) {
        RelationDefinition definition = new RelationDefinition();
        definition.setId(id);
        definition.setKind(RelationKind.COMPLEX);
        definition.setSymmetric(symmetric);
        return definition;
    }

    private static Concept concept(long id, String externalId) {
        Concept concept = new Concept();
        concept.setId(id);
        concept.setExternalId(externalId);
        return concept;
    }
}
