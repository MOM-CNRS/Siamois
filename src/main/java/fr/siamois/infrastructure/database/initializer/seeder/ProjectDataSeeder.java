package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.misc.ImportProgress;
import fr.siamois.domain.models.misc.SeedCounts;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.dataimport.ImportSchema;
import fr.siamois.utils.MessageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ProjectDataSeeder {

    private final RecordingUnitSeeder recordingUnitSeeder;
    private final SpecimenSeeder specimenSeeder;
    private final RecordingUnitRelSeeder recordingUnitRelSeeder;
    private final RecordingUnitStratiRelSeeder recordingUnitStratiRelSeeder;
    private final SpatialUnitSeeder spatialUnitSeeder;
    private final SpatialUnitRelSeeder spatialUnitRelSeeder;
    private final PhaseSeeder phaseSeeder;
    private final ActionUnitRepository actionUnitRepository;


    @Transactional
    public void seedAll(ImportSpecs spec, ActionUnitDTO project, ImportProgress progress, SeedCounts seedCounts) {
        int total = spec.spatialUnits().size() + spec.phaseSpecs().size() + spec.recordingUnits().size()
                + spec.specimenSpecs().size() + spec.recordingUnitStratiRelSpecs().size() + spec.recordingUnitRelSpecs().size()
                + spec.spatialUnitRelSpecs().size();
        progress.start(ImportProgress.Phase.PERSISTING, total);

        // RecordingUnitSeeder, SpecimenSeeder, PhaseSeeder, and RecordingUnitStratiRelSeeder all
        // self-report progress per 100-row flush/clear chunk internally — don't also advance after
        // those steps, that would double-count. SpatialUnitSeeder, RecordingUnitRelSeeder and
        // SpatialUnitRelSeeder use a single non-chunked saveAll (self-referencing children/hierarchy),
        // so there's no finer signal to report for those and they still advance in one lump after the step.
        AtomicReference<Map<String, SpatialUnit>> seededPlaces = new AtomicReference<>(Map.of());
        seedStep(ImportSchema.SPATIAL_UNIT, () -> seededPlaces.set(spatialUnitSeeder.seed(spec.spatialUnits(), seedCounts)));
        seedStep(ImportSchema.SPATIAL_UNIT, () -> addToSpatialContext(project, seededPlaces.get()));
        progress.advance(spec.spatialUnits().size());
        seedStep(ImportSchema.SPATIAL_UNIT_REL, () -> spatialUnitRelSeeder.seed(spec.spatialUnitRelSpecs(),
                project.getCreatedByInstitution().getId()));
        progress.advance(spec.spatialUnitRelSpecs().size());
        seedStep(ImportSchema.PHASE, () -> phaseSeeder.seed(spec.phaseSpecs(), progress, seedCounts));
        seedStep(ImportSchema.RECORDING_UNIT, () -> recordingUnitSeeder.seed(spec.recordingUnits(), progress, seedCounts));
        seedStep(ImportSchema.SPECIMEN, () -> specimenSeeder.seed(spec.specimenSpecs(), project.getCreatedByInstitution().getId(), progress, seedCounts));
        seedStep(ImportSchema.STRATI_REL, () -> recordingUnitStratiRelSeeder.seed(spec.recordingUnitStratiRelSpecs(),
                project.getCreatedByInstitution().getId(), project.getFullIdentifier(), progress));
        seedStep(ImportSchema.RECORDING_REL, () -> recordingUnitRelSeeder.seed(spec.recordingUnitRelSpecs(),
                project.getCreatedByInstitution().getId(), project.getFullIdentifier()));
        progress.advance(spec.recordingUnitRelSpecs().size());
    }

    /** Every place of the Lieu sheet becomes part of the project's "localisation précise" (its spatial context). */
    private void addToSpatialContext(ActionUnitDTO project, Map<String, SpatialUnit> places) {
        if (places.isEmpty() || project == null || project.getId() == null) {
            return;
        }
        ActionUnit entity = actionUnitRepository.findById(project.getId()).orElse(null);
        if (entity == null) {
            return;
        }
        entity.getSpatialContext().addAll(places.values());
        actionUnitRepository.save(entity);
    }

    /** Runs one seeding step, re-throwing any failure tagged with the table ID it came from. */
    private void seedStep(String tableId, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            throw new SeedException(tableId, MessageUtils.describe(e), e);
        }
    }

}
