package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class RecordingUnitRelSeederTest {

    private RecordingUnitSeeder recordingUnitSeeder;
    private RecordingUnitRepository recordingUnitRepository;
    private RecordingUnitRelSeeder relSeeder;

    @BeforeEach
    void setUp() {
        recordingUnitSeeder = mock(RecordingUnitSeeder.class);
        recordingUnitRepository = mock(RecordingUnitRepository.class);
        relSeeder = new RecordingUnitRelSeeder(recordingUnitSeeder, recordingUnitRepository);
    }

    private void stubBulkLookup(RecordingUnit... units) {
        Map<RecordingUnitSeeder.RecordingUnitKey, RecordingUnit> byKey = new HashMap<>();
        for (RecordingUnit u : units) {
            byKey.put(new RecordingUnitSeeder.RecordingUnitKey(u.getFullIdentifier(), ""), u);
        }
        when(recordingUnitSeeder.bulkGetRecordingUnitsFromKeys(anyCollection(), eq(1L))).thenReturn(byKey);
    }

    @Test
    void seed_shouldAssociateChildrenToParents_andSaveAll() {
        RecordingUnit parent1 = new RecordingUnit(); parent1.setFullIdentifier("P1");
        RecordingUnit parent2 = new RecordingUnit(); parent2.setFullIdentifier("P2");
        RecordingUnit child1 = new RecordingUnit();  child1.setFullIdentifier("C1");
        RecordingUnit child2 = new RecordingUnit();  child2.setFullIdentifier("C2");

        stubBulkLookup(parent1, parent2, child1, child2);

        List<RecordingUnitRelSeeder.RecordingUnitRelDTO> specs = List.of(
                new RecordingUnitRelSeeder.RecordingUnitRelDTO("P1", "C1"),
                new RecordingUnitRelSeeder.RecordingUnitRelDTO("P1", "C2"),
                new RecordingUnitRelSeeder.RecordingUnitRelDTO("P2", "C1")
        );

        relSeeder.seed(specs, 1L, "");

        assertThat(parent1.getChildren()).containsExactlyInAnyOrder(child1, child2);
        assertThat(parent2.getChildren()).containsExactly(child1);

        ArgumentCaptor<List<RecordingUnit>> captor = ArgumentCaptor.forClass(List.class);
        verify(recordingUnitRepository).saveAll(captor.capture());

        List<RecordingUnit> savedParents = captor.getValue();
        assertThat(savedParents).containsExactlyInAnyOrder(parent1, parent2);
    }

    @Test
    void seed_missingChild_throwsWithLineContext() {
        RecordingUnit parent = new RecordingUnit(); parent.setFullIdentifier("P1");
        stubBulkLookup(parent);
        // "C1" isn't in the bulk lookup result -> missing

        List<RecordingUnitRelSeeder.RecordingUnitRelDTO> specs = List.of(
                new RecordingUnitRelSeeder.RecordingUnitRelDTO("P1", "C1")
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> relSeeder.seed(specs, 1L, ""));

        assertThat(ex.getMessage()).contains("[Relation UE ligne 1]").contains("Recording unit introuvable");
        verify(recordingUnitRepository, never()).saveAll(any());
    }

    @Test
    void seed_emptySpecs_noInteractionWithRepository() {
        relSeeder.seed(List.of(), 1L, "");

        verify(recordingUnitRepository, never()).saveAll(any());
    }
}
