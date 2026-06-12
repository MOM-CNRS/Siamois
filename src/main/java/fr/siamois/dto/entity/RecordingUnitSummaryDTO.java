package fr.siamois.dto.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class RecordingUnitSummaryDTO extends AbstractEntityDTO {


    private String identifier;
    private String fullIdentifier;
    private ConceptDTO type;

    /** Jackson delegating creator: handles bare-number JSON (ID only) sent by some PrimeFaces paths. */
    @JsonCreator
    public RecordingUnitSummaryDTO(Long id) {
        this.id = id;
    }

    public RecordingUnitSummaryDTO(RecordingUnitSummaryDTO original) {
        identifier = original.getIdentifier();
        fullIdentifier = original.getFullIdentifier();
        type = original.getType();
        id = original.getId();
        createdByInstitution = original.getCreatedByInstitution();
    }

    public RecordingUnitSummaryDTO(RecordingUnitDTO plain) {
        identifier = plain.getIdentifier();
        id = plain.getId();
        fullIdentifier = plain.getFullIdentifier();
        type = plain.getType();
    }
}
