package fr.siamois.infrastructure.database.initializer.seeder;

import java.util.List;

public record ImportSpecs(List<InstitutionSeeder.InstitutionSpec> institutions, List<PersonSeeder.PersonSpec> persons,
                          List<SpatialUnitSeeder.SpatialUnitSpecs> spatialUnits,
                          List<ActionCodeSeeder.ActionCodeSpec> actionCodes,
                          List<ActionUnitSeeder.ActionUnitSpecs> actionUnits,
                          List<RecordingUnitSeeder.RecordingUnitSpecs> recordingUnits,
                          List<SpecimenSeeder.SpecimenSpecs> specimenSpecs,
                          List<RecordingUnitRelSeeder.RecordingUnitDTO> recordingUnitRelSpecs,
                          List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> recordingUnitStratiRelSpecs) {

}

