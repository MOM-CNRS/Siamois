package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.dto.entity.ActionUnitDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectDataSeeder {

    private final RecordingUnitSeeder recordingUnitSeeder;
    private final SpecimenSeeder specimenSeeder;
    private final RecordingUnitRelSeeder recordingUnitRelSeeder;
    private final RecordingUnitStratiRelSeeder recordingUnitStratiRelSeeder;

    public void seedAll(ImportSpecs spec, ActionUnitDTO project) {
        recordingUnitSeeder.seed(spec.recordingUnits());
        specimenSeeder.seed(spec.specimenSpecs(),project.getCreatedByInstitution().getId());
        recordingUnitStratiRelSeeder.seed(spec.recordingUnitStratiRelSpecs(), project.getCreatedByInstitution().getId());
        recordingUnitRelSeeder.seed(spec.recordingUnitRelSpecs(), project.getCreatedByInstitution().getId());
    }

}
