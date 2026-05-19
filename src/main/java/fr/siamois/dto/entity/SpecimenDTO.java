package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SpecimenDTO extends AbstractEntityDTO {

    private Integer identifier;
    private String fullIdentifier;
    private ConceptDTO type;
    private ConceptDTO category;
    private List<PersonDTO> authors;
    private List<PersonDTO> collectors;
    private RecordingUnitSummaryDTO recordingUnit;
    protected OffsetDateTime collectionDate;
    private Set<SpecimenSummaryDTO> parents;
    private Set<SpecimenSummaryDTO> children;


    public SpecimenDTO(SpecimenDTO original) {
    }

    public static List<String> getBindableFieldNames() {
        return List.of("collectionDate", "collectors", "fullIdentifier", "authors", "recordingUnit",
                "parents", "children",
                "type", "category");
    }
}
