package fr.siamois.dto.entity;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class SpecimenDTO extends AbstractEntityDTO {

    private int identifier;
    private ConceptDTO type;
    private String fullIdentifier;
    private Boolean validated;
    private List<PersonDTO> authors;
    private List<PersonDTO> collectors;
    private RecordingUnitDTO recordingUnit;
    protected OffsetDateTime collectionDate;


    public SpecimenDTO(SpecimenDTO original) {
    }
}
