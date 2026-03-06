package fr.siamois.dto.entity;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SpecimenDTO extends AbstractEntityDTO {

    private Integer identifier;
    private ConceptDTO type;
    private String fullIdentifier;
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
