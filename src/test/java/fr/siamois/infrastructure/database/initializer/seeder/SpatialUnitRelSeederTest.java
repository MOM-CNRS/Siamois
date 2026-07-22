package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class SpatialUnitRelSeederTest {

    private SpatialUnitRepository spatialUnitRepository;
    private SpatialUnitRelSeeder relSeeder;

    @BeforeEach
    void setUp() {
        spatialUnitRepository = mock(SpatialUnitRepository.class);
        relSeeder = new SpatialUnitRelSeeder(spatialUnitRepository);
    }

    private void stubBulkLookup(SpatialUnit... units) {
        when(spatialUnitRepository.findAllByNameInAndInstitution(anyCollection(), eq(1L))).thenReturn(List.of(units));
    }

    private long nextId = 1L;

    // SpatialUnit.equals()/hashCode() compare by id, unlike RecordingUnit which uses identity —
    // real persisted rows always have a distinct non-null id, so every test double needs one too,
    // or unsaved entities (id == null) would all compare equal and collapse in a Set.
    private SpatialUnit unit(String name) {
        SpatialUnit su = new SpatialUnit();
        su.setId(nextId++);
        su.setName(name);
        return su;
    }

    @Test
    void seed_shouldAssociateChildrenToParents_andSaveAll() {
        SpatialUnit parent1 = unit("Parcelle A");
        SpatialUnit parent2 = unit("Parcelle B");
        SpatialUnit child1 = unit("US 1");
        SpatialUnit child2 = unit("US 2");

        stubBulkLookup(parent1, parent2, child1, child2);

        List<SpatialUnitRelSeeder.SpatialUnitRelDTO> specs = List.of(
                new SpatialUnitRelSeeder.SpatialUnitRelDTO("Parcelle A", "US 1"),
                new SpatialUnitRelSeeder.SpatialUnitRelDTO("Parcelle A", "US 2"),
                new SpatialUnitRelSeeder.SpatialUnitRelDTO("Parcelle B", "US 1")
        );

        relSeeder.seed(specs, 1L);

        assertThat(parent1.getChildren()).containsExactlyInAnyOrder(child1, child2);
        assertThat(parent2.getChildren()).containsExactly(child1);

        ArgumentCaptor<List<SpatialUnit>> captor = ArgumentCaptor.forClass(List.class);
        verify(spatialUnitRepository).saveAll(captor.capture());

        List<SpatialUnit> savedParents = captor.getValue();
        assertThat(savedParents).containsExactlyInAnyOrder(parent1, parent2);
    }

    @Test
    void seed_missingChild_throwsWithLineContext() {
        SpatialUnit parent = unit("Parcelle A");
        stubBulkLookup(parent);
        // "US 1" isn't in the bulk lookup result -> missing

        List<SpatialUnitRelSeeder.SpatialUnitRelDTO> specs = List.of(
                new SpatialUnitRelSeeder.SpatialUnitRelDTO("Parcelle A", "US 1")
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> relSeeder.seed(specs, 1L));

        assertThat(ex.getMessage()).contains("[Relation Lieu ligne 1]").contains("Lieu introuvable");
        verify(spatialUnitRepository, never()).saveAll(any());
    }

    @Test
    void seed_emptySpecs_noInteractionWithRepository() {
        relSeeder.seed(List.of(), 1L);

        verify(spatialUnitRepository, never()).saveAll(any());
    }
}
