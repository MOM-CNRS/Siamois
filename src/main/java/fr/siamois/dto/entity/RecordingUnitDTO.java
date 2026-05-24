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

  /** Révision serveur pour détection de conflit (optimistic locking). */
    protected Long syncRevision;

    protected String identifier;
    protected String fullIdentifier;
    protected ConceptDTO type;
    protected ActionUnitSummaryDTO actionUnit;
    protected Integer parentsCount;
    protected Integer childrenCount;
    protected OffsetDateTime openingDate;
    protected OffsetDateTime closingDate;
    protected ConceptDTO geomorphologicalCycle;
    protected ConceptDTO geomorphologicalAgent;
    protected ConceptDTO normalizedInterpretation;
    protected Long specimenCount;
    /** Nombre de relations stratigraphiques (unité en tant qu'extrémité 1 ou 2). */
    protected Long relationshipCount;
    protected String matrixColor;
    protected PersonDTO author;
    protected String description;
    protected List<PersonDTO> contributors = new ArrayList<>();
    protected SpatialUnitSummaryDTO spatialUnit;
    protected String comments;
    protected Set<StratigraphicRelationshipDTO> relationshipsAsUnit1 = new HashSet<>();
    protected Set<StratigraphicRelationshipDTO> relationshipsAsUnit2 = new HashSet<>();
    protected Integer taq;
    protected Integer tpq;
    protected ConceptDTO chronologicalPhase;
    protected ConceptDTO erosionShape;
    protected ConceptDTO erosionOrientation;
    protected ConceptDTO erosionProfile;
    protected MeasurementAnswerDTO zInf;
    protected MeasurementAnswerDTO zSup;

    // Write dto
    private Set<RecordingUnitSummaryDTO> parents;
    private Set<RecordingUnitSummaryDTO> children;


    public RecordingUnitDTO(RecordingUnitDTO original) {
        type = original.getType();
        actionUnit = original.getActionUnit();
        createdByInstitution = original.getCreatedByInstitution();
        description = original.getDescription();
        spatialUnit = original.getSpatialUnit();
        matrixColor = original.getMatrixColor();
        relationshipCount = original.getRelationshipCount();
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
        return List.of("creationTime", "openingDate", "closingDate", "description", "identifier",
                "contributors", "type", "secondaryType", "thirdType", "actionUnit", "spatialUnit",
                "parents", "children", "comments", "zInf", "zSup",
                "geomorphologicalCycle", "normalizedInterpretation", "author", "geomorphologicalAgent",
                "matrixComposition", "matrixColor", "matrixTexture", "erosionShape", "erosionOrientation",
                "erosionProfile", "taq", "tpq", "chronologicalPhase", "fullIdentifier");
    }
}
