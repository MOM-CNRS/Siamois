package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectDataSeederTest {

    @Mock RecordingUnitSeeder recordingUnitSeeder;
    @Mock SpecimenSeeder specimenSeeder;
    @Mock RecordingUnitRelSeeder recordingUnitRelSeeder;
    @Mock RecordingUnitStratiRelSeeder recordingUnitStratiRelSeeder;
    @Mock SpatialUnitSeeder spatialUnitSeeder;
    @Mock PhaseSeeder phaseSeeder;

    @InjectMocks
    ProjectDataSeeder seeder;

    private ActionUnitDTO project;
    private ImportSpecs emptySpecs;

    @BeforeEach
    void setUp() {
        InstitutionDTO institution = new InstitutionDTO();
        institution.setId(42L);

        project = new ActionUnitDTO();
        project.setFullIdentifier("UA-FULL-001");
        project.setCreatedByInstitution(institution);

        emptySpecs = new ImportSpecs(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }







}
