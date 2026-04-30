package fr.siamois.dto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class RecordingUnitDTO extends AbstractEntityDTO {

    private String identifier;
    private String fullIdentifier;
    private ConceptDTO type;
    private ActionUnitSummaryDTO actionUnit;
    private Set<RecordingUnitSummaryDTO> parents = new HashSet<>();
    private Set<RecordingUnitSummaryDTO> children= new HashSet<>();
    private OffsetDateTime openingDate;
    private OffsetDateTime closingDate;
    private ConceptDTO geomorphologicalCycle;
    private ConceptDTO geomorphologicalAgent;
    private ConceptDTO normalizedInterpretation;
    private Set<SpecimenSummaryDTO> specimenList = new HashSet<>();
    private Long specimenCount;
    private PersonDTO author;
    private String description;
    private List<PersonDTO> contributors = new ArrayList<>();
    private SpatialUnitSummaryDTO spatialUnit;
    private String comments;
    private Set<StratigraphicRelationshipDTO> relationshipsAsUnit1 = new HashSet<>();
    private Set<StratigraphicRelationshipDTO> relationshipsAsUnit2 = new HashSet<>();
    private Integer taq;
    private Integer tpq;
    private ConceptDTO chronologicalPhase;
    private ConceptDTO erosionShape;
    private ConceptDTO erosionOrientation;
    private ConceptDTO erosionProfile;
    private MeasurementAnswerDTO zInf;
    private MeasurementAnswerDTO zSup;


    public RecordingUnitDTO(RecordingUnitDTO original) {
        type = original.getType();
        actionUnit = original.getActionUnit();
        createdByInstitution = original.getCreatedByInstitution();
        description = original.getDescription();
        spatialUnit = original.getSpatialUnit();
    }

    /**
     * Resets the full identifier to it's base format.
     */
    public void resetFullIdentifier() {
        if (actionUnit == null) return;
        fullIdentifier = actionUnit.getFullIdentifier();
    }

    @Override
    public String toString() {
        return "RecordingUnitDTO{" +
                ", fullIdentifier='" + fullIdentifier + '\'' +
                '}';
    }

    @JsonIgnore
    @Transient
    public List<String> getBindableFieldNames() {
        return List.of("creationTime", "openingDate", "closingDate", "description","identifier",
                "contributors", "type", "secondaryType", "thirdType", "actionUnit", "spatialUnit",
                "parents","children", "comments", "zInf", "zSup",
                "geomorphologicalCycle", "normalizedInterpretation", "author", "geomorphologicalAgent",
                "matrixComposition", "matrixColor", "matrixTexture", "erosionShape", "erosionOrientation",
                "erosionProfile", "taq", "tpq", "chronologicalPhase", "fullIdentifier");
    }
}
