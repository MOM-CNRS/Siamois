package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SpecimenDTO extends AbstractEntityDTO {

    private Integer identifier;
    private String fullIdentifier;
    private ConceptDTO type;
    private List<PersonDTO> authors;
    private List<PersonDTO> collectors;
    private RecordingUnitDTO recordingUnit;
    protected OffsetDateTime collectionDate;


    public SpecimenDTO(SpecimenDTO original) {
    }

    public static List<String> getBindableFieldNames() {
        return List.of("collectionDate", "collectors", "fullIdentifier", "authors",
                "type", "category");
    }
}
