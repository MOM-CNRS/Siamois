package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.misc.SeedCounts;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDataSeederTest {

    @Mock private RecordingUnitSeeder recordingUnitSeeder;
    @Mock private SpecimenSeeder specimenSeeder;
    @Mock private RecordingUnitRelSeeder recordingUnitRelSeeder;
    @Mock private RecordingUnitStratiRelSeeder recordingUnitStratiRelSeeder;
    @Mock private SpatialUnitSeeder spatialUnitSeeder;
    @Mock private SpatialUnitRelSeeder spatialUnitRelSeeder;
    @Mock private PhaseSeeder phaseSeeder;
    @Mock private ActionUnitRepository actionUnitRepository;
    @InjectMocks private ProjectDataSeeder seeder;

    private static ImportSpecs emptySpecs() {
        return new ImportSpecs(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    private static ActionUnitDTO project() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(1L);
        ActionUnitDTO project = new ActionUnitDTO();
        project.setId(10L);
        project.setCreatedByInstitution(institution);
        return project;
    }

    @Test
    void seedAll_addsEveryPlaceOfTheSheetToTheProjectSpatialContext() {
        SpatialUnit a = new SpatialUnit();
        SpatialUnit b = new SpatialUnit();
        a.setId(1L);
        b.setId(2L);
        ActionUnit entity = new ActionUnit();
        when(spatialUnitSeeder.seed(any(), any())).thenReturn(Map.of("A", a, "B", b));
        when(actionUnitRepository.findById(10L)).thenReturn(Optional.of(entity));

        seeder.seedAll(emptySpecs(), project(), new ImportProgress(), new SeedCounts());

        assertEquals(2, entity.getSpatialContext().size());
        verify(actionUnitRepository).save(entity);
    }

    @Test
    void seedAll_withoutPlaces_leavesTheProjectUntouched() {
        when(spatialUnitSeeder.seed(any(), any())).thenReturn(Map.of());

        seeder.seedAll(emptySpecs(), project(), new ImportProgress(), new SeedCounts());

        verify(actionUnitRepository, never()).save(any());
    }
}
