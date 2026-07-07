package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.recordingunit.StratigraphicRelationshipRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.ConceptRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingUnitStratiRelSeederTest {

    @Mock StratigraphicRelationshipRepository stratigraphicRelationshipRepository;
    @Mock RecordingUnitSeeder recordingUnitSeeder;
    @Mock ConceptRepository conceptRepository;
    @Mock EntityManager entityManager;

    @InjectMocks
    RecordingUnitStratiRelSeeder seeder;

    private static final String VOCABULARY_ID = "th240";
    private static final Long INSTITUTION_ID = 1L;
    private static final String ACTION_UNIT_ID = "action-01";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seeder, "entityManager", entityManager);
    }

    private RecordingUnit unit(String fullIdentifier, long id) {
        RecordingUnit ru = new RecordingUnit();
        ru.setFullIdentifier(fullIdentifier);
        ru.setId(id);
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
        Concept c = new Concept();
        c.setExternalId("4287541");
        when(conceptRepository.findAllByExternalVocabularyIdIgnoreCaseAndExternalIdIgnoreCaseIn(eq(VOCABULARY_ID), anyCollection()))
                .thenReturn(List.of(c));
    }

    @Test
    void seed_us1DoesNotExist_throws() {
        stubRecordingUnits(unit("US2", 2L));
        // "US1" isn't in the bulk lookup result -> missing

        var specs = List.of(new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(
                "US1", "US2", null, false, false, false));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(specs, INSTITUTION_ID, ACTION_UNIT_ID));

        assertThat(ex.getMessage()).contains("[Relation strati ligne 1]").contains("Recording unit introuvable");
    }

    @Test
    void seed_relNull_silentlySkipped() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));

        var specs = List.of(new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(
                "US1", "US2", null, false, false, false));

        seeder.seed(specs, INSTITUTION_ID, ACTION_UNIT_ID);

        verify(stratigraphicRelationshipRepository, never()).saveAll(any());
    }

    @Test
    void seed_conceptNotFound_throws() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));
        // conceptRepository left unstubbed -> empty -> concept not found

        var specs = List.of(new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(
                "US1", "US2", new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"), false, false, false));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> seeder.seed(specs, INSTITUTION_ID, ACTION_UNIT_ID));

        assertThat(ex.getMessage()).contains("Concept").contains("introuvable");
    }

    @Test
    void seed_alreadyExists_saveNeverCalled() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));
        stubConceptFound();

        StratigraphicRelationship existing = new StratigraphicRelationship();
        when(stratigraphicRelationshipRepository.findAllByUnit1IdIn(anyCollection()))
                .thenAnswer(inv -> {
                    RecordingUnit u1 = unit("US1", 1L);
                    RecordingUnit u2 = unit("US2", 2L);
                    existing.setUnit1(u1);
                    existing.setUnit2(u2);
                    return List.of(existing);
                });

        var specs = List.of(new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(
                "US1", "US2", new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"), false, false, false));

        seeder.seed(specs, INSTITUTION_ID, ACTION_UNIT_ID);

        verify(stratigraphicRelationshipRepository, never()).saveAll(any());
    }

    @Test
    void seed_created() {
        stubRecordingUnits(unit("US1", 1L), unit("US2", 2L));
        stubConceptFound();
        // stratigraphicRelationshipRepository bulk-existence lookup left unstubbed -> empty -> not already present

        var specs = List.of(new RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO(
                "US1", "US2", new ConceptSeeder.ConceptKey(VOCABULARY_ID, "4287541"), true, false, false));

        seeder.seed(specs, INSTITUTION_ID, ACTION_UNIT_ID);

        verify(stratigraphicRelationshipRepository, times(1)).saveAll(argThat(list -> {
            int count = 0;
            for (var ignored : list) count++;
            return count == 1;
        }));
        verify(entityManager, times(1)).flush();
        verify(entityManager, times(1)).clear();
    }
}
