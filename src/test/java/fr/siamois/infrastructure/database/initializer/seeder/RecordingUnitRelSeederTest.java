package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void seed_shouldAssociateChildrenToParents_andSaveAll() {
        // Préparer les mocks
        RecordingUnit parent1 = new RecordingUnit(); parent1.setFullIdentifier("P1");
        RecordingUnit parent2 = new RecordingUnit(); parent2.setFullIdentifier("P2");
        RecordingUnit child1 = new RecordingUnit();  child1.setFullIdentifier("C1");
        RecordingUnit child2 = new RecordingUnit();   child2.setFullIdentifier("C2");

        // Mockito : renvoyer les objets à partir des clés
        when(recordingUnitSeeder.getRecordingUnitFromKey(new RecordingUnitSeeder.RecordingUnitKey("P1")))
                .thenReturn(parent1);
        when(recordingUnitSeeder.getRecordingUnitFromKey(new RecordingUnitSeeder.RecordingUnitKey("P2")))
                .thenReturn(parent2);
        when(recordingUnitSeeder.getRecordingUnitFromKey(new RecordingUnitSeeder.RecordingUnitKey("C1")))
                .thenReturn(child1);
        when(recordingUnitSeeder.getRecordingUnitFromKey(new RecordingUnitSeeder.RecordingUnitKey("C2")))
                .thenReturn(child2);

        // Données de test
        List<RecordingUnitRelSeeder.RecordingUnitDTO> specs = List.of(
                new RecordingUnitRelSeeder.RecordingUnitDTO("P1", "C1"),
                new RecordingUnitRelSeeder.RecordingUnitDTO("P1", "C2"),
                new RecordingUnitRelSeeder.RecordingUnitDTO("P2", "C1")
        );

        // Appel de la méthode
        relSeeder.seed(specs);

        // Vérifier que les enfants ont été ajoutés aux parents
        assertThat(parent1.getChildren()).containsExactlyInAnyOrder(child1, child2);
        assertThat(parent2.getChildren()).containsExactly(child1);

        // Vérifier que saveAll a été appelé avec les bons parents
        ArgumentCaptor<List<RecordingUnit>> captor = ArgumentCaptor.forClass(List.class);
        verify(recordingUnitRepository).saveAll(captor.capture());

        List<RecordingUnit> savedParents = captor.getValue();
        assertThat(savedParents).containsExactlyInAnyOrder(parent1, parent2);
    }


    @Test
    void seed_shouldHandleNonExistentChildGracefully() {
        RecordingUnit parent = new RecordingUnit(); parent.setFullIdentifier("P1");
        parent.setChildren(new HashSet<>());

        when(recordingUnitSeeder.getRecordingUnitFromKey(new RecordingUnitSeeder.RecordingUnitKey("P1")))
                .thenReturn(parent);
        // Simuler un enfant introuvable (null)
        when(recordingUnitSeeder.getRecordingUnitFromKey(new RecordingUnitSeeder.RecordingUnitKey("C1")))
                .thenReturn(null);

        List<RecordingUnitRelSeeder.RecordingUnitDTO> specs = List.of(
                new RecordingUnitRelSeeder.RecordingUnitDTO("P1", "C1")
        );

        relSeeder.seed(specs);

        // Aucun enfant ajouté
        assertThat(parent.getChildren()).isEmpty();

        // Vérifier que saveAll est quand même appelé avec le parent
        verify(recordingUnitRepository).saveAll(List.of());
    }

    }
