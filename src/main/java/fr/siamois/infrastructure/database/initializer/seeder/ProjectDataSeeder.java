package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectDataSeeder {

    private final RecordingUnitSeeder recordingUnitSeeder;
    private final SpecimenSeeder specimenSeeder;
    private final RecordingUnitRelSeeder recordingUnitRelSeeder;
    private final RecordingUnitStratiRelSeeder recordingUnitStratiRelSeeder;
    private final SpatialUnitSeeder spatialUnitSeeder;
    private final PhaseSeeder phaseSeeder;

    public void seedAll(ImportSpecs spec, ActionUnitDTO project) {
        seedStep(ImportSchema.SPATIAL_UNIT, () -> spatialUnitSeeder.seed(spec.spatialUnits()));
        seedStep(ImportSchema.PHASE, () -> phaseSeeder.seed(spec.phaseSpecs()));
        seedStep(ImportSchema.RECORDING_UNIT, () -> recordingUnitSeeder.seed(spec.recordingUnits()));
        seedStep(ImportSchema.SPECIMEN, () -> specimenSeeder.seed(spec.specimenSpecs(), project.getCreatedByInstitution().getId()));
        seedStep(ImportSchema.STRATI_REL, () -> recordingUnitStratiRelSeeder.seed(spec.recordingUnitStratiRelSpecs(),
                project.getCreatedByInstitution().getId(), project.getFullIdentifier()));
        seedStep(ImportSchema.RECORDING_REL, () -> recordingUnitRelSeeder.seed(spec.recordingUnitRelSpecs(),
                project.getCreatedByInstitution().getId(), project.getFullIdentifier()));
    }

    /** Runs one seeding step, re-throwing any failure tagged with the table ID it came from. */
    private void seedStep(String tableId, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            throw new SeedException(tableId, e.getMessage(), e);
        }
    }

}
