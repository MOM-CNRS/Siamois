package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectDataSeeder {

    private final RecordingUnitSeeder recordingUnitSeeder;
    private final SpecimenSeeder specimenSeeder;
    private final RecordingUnitRelSeeder recordingUnitRelSeeder;
    private final RecordingUnitStratiRelSeeder recordingUnitStratiRelSeeder;
    private final SpatialUnitSeeder spatialUnitSeeder;
    private final PhaseSeeder phaseSeeder;


    @Transactional
    public void seedAll(ImportSpecs spec, ActionUnitDTO project, ImportProgress progress) {
        int total = spec.spatialUnits().size() + spec.phaseSpecs().size() + spec.recordingUnits().size()
                + spec.specimenSpecs().size() + spec.recordingUnitStratiRelSpecs().size() + spec.recordingUnitRelSpecs().size();
        progress.start(ImportProgress.Phase.PERSISTING, total);

        // RecordingUnitSeeder, SpecimenSeeder, PhaseSeeder, and RecordingUnitStratiRelSeeder all
        // self-report progress per 100-row flush/clear chunk internally — don't also advance after
        // those steps, that would double-count. SpatialUnitSeeder and RecordingUnitRelSeeder use a
        // single non-chunked saveAll (self-referencing children/hierarchy), so there's no finer
        // signal to report for those two and they still advance in one lump after the step.
        seedStep(ImportSchema.SPATIAL_UNIT, () -> spatialUnitSeeder.seed(spec.spatialUnits()));
        progress.advance(spec.spatialUnits().size());
        seedStep(ImportSchema.PHASE, () -> phaseSeeder.seed(spec.phaseSpecs(), progress));
        seedStep(ImportSchema.RECORDING_UNIT, () -> recordingUnitSeeder.seed(spec.recordingUnits(), progress));
        seedStep(ImportSchema.SPECIMEN, () -> specimenSeeder.seed(spec.specimenSpecs(), project.getCreatedByInstitution().getId(), progress));
        seedStep(ImportSchema.STRATI_REL, () -> recordingUnitStratiRelSeeder.seed(spec.recordingUnitStratiRelSpecs(),
                project.getCreatedByInstitution().getId(), project.getFullIdentifier(), progress));
        seedStep(ImportSchema.RECORDING_REL, () -> recordingUnitRelSeeder.seed(spec.recordingUnitRelSpecs(),
                project.getCreatedByInstitution().getId(), project.getFullIdentifier()));
        progress.advance(spec.recordingUnitRelSpecs().size());
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
