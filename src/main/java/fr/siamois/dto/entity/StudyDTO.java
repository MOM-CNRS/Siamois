package fr.siamois.dto.entity;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class StudyDTO extends AbstractEntityDTO {

    protected String identifier;
    protected Integer generatedNumber;
    protected ActionUnitSummaryDTO actionUnit;
    protected ConceptDTO type;
    protected OffsetDateTime studyDate;
    protected String reportCode;
    protected OffsetDateTime treatmentStart;
    protected OffsetDateTime treatmentEnd;
    protected String treatmentPlace;
    protected String treatmentManager;
    protected String treatmentIdentifier;
    protected String treatmentComments;

    /** The linked recording units and finds: the links are written from the study's side. */
    private Set<RecordingUnitSummaryDTO> recordingUnits = new HashSet<>();
    private Set<SpecimenSummaryDTO> specimens = new HashSet<>();

    public static List<String> getBindableFieldNames() {
        return List.of("identifier", "generatedNumber", "actionUnit", "type", "studyDate",
                "reportCode", "treatmentStart", "treatmentEnd", "treatmentPlace", "treatmentManager",
                "treatmentIdentifier", "treatmentComments", "recordingUnits", "specimens");
    }

}
