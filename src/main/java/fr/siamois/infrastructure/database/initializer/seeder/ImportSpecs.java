package fr.siamois.infrastructure.database.initializer.seeder;

import lombok.Getter;

import java.util.List;

@Getter
public class ImportSpecs {

    private final List<InstitutionSeeder.InstitutionSpec> institutions;
    private final List<PersonSeeder.PersonSpec> persons;
    private final List<SpatialUnitSeeder.SpatialUnitSpecs> spatialUnits;
    private final List<ActionCodeSeeder.ActionCodeSpec> actionCodes;
    private final List<ActionUnitSeeder.ActionUnitSpecs> actionUnits;
    private final List<RecordingUnitSeeder.RecordingUnitSpecs> recordingUnits;
    private final List<SpecimenSeeder.SpecimenSpecs> specimenSpecs;
    private final List<RecordingUnitRelSeeder.RecordingUnitDTO> recordingUnitRelSpecs;
    private final List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> recordingUnitStratiRelSpecs;

    public ImportSpecs(List<InstitutionSeeder.InstitutionSpec> institutions,
                       List<PersonSeeder.PersonSpec> persons,
                       List<SpatialUnitSeeder.SpatialUnitSpecs> spatialUnits,
                       List<ActionCodeSeeder.ActionCodeSpec> actionCodes, List<ActionUnitSeeder.ActionUnitSpecs> actionUnits, List<RecordingUnitSeeder.RecordingUnitSpecs> recordingUnits,
                       List<SpecimenSeeder.SpecimenSpecs> specimenSpecs,
                       List<RecordingUnitRelSeeder.RecordingUnitDTO> recordingUnitSpecs, List<RecordingUnitStratiRelSeeder.RecordingUnitStratiRelDTO> recordingUnitStratiRelSpecs) {
        this.institutions = institutions;
        this.persons = persons;
        this.spatialUnits = spatialUnits;
        this.actionCodes = actionCodes;
        this.actionUnits = actionUnits;
        this.recordingUnits = recordingUnits;
        this.specimenSpecs = specimenSpecs;
        this.recordingUnitRelSpecs = recordingUnitSpecs;
        this.recordingUnitStratiRelSpecs = recordingUnitStratiRelSpecs;
    }

}

