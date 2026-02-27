package fr.siamois.dto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class RecordingUnitDTO extends AbstractEntityDTO {

    private String identifier;
    private String fullIdentifier;
    private ConceptDTO type;
    private ActionUnitSummaryDTO actionUnit;
    private Set<RecordingUnitSummaryDTO> parents;
    private Set<RecordingUnitSummaryDTO> children;
    private OffsetDateTime openingDate;
    private OffsetDateTime closingDate;
    private Set<SpecimenSummaryDTO> specimenList;
    private PersonDTO author;
    private List<PersonDTO> contributors;
    private SpatialUnitSummaryDTO spatialUnit;
    private Set<StratigraphicRelationshipDTO> relationshipsAsUnit1 ;
    private Set<StratigraphicRelationshipDTO> relationshipsAsUnit2 ;
    private Boolean validated;

    public RecordingUnitDTO(RecordingUnitDTO original) {
        identifier = original.getIdentifier();
        fullIdentifier = original.getFullIdentifier();
        type = original.getType();
        actionUnit = original.getActionUnit();
    }

    /**
     * Resets the full identifier to it's base format.
     */
    public void resetFullIdentifier() {
        if (actionUnit == null) return;
        fullIdentifier = actionUnit.getFullIdentifier();
    }

    public List<String> getBindableFieldNames() {
        return List.of("creationTime", "openingDate", "closingDate", "description","identifier",
                "contributors", "type", "secondaryType", "thirdType", "actionUnit", "spatialUnit",
                "geomorphologicalCycle", "normalizedInterpretation", "author", "geomorphologicalAgent",
                "matrixComposition", "matrixColor", "matrixTexture", "erosionShape", "erosionOrientation",
                "erosionProfile", "taq", "tpq", "chronologicalPhase", "fullIdentifier");
    }
}
