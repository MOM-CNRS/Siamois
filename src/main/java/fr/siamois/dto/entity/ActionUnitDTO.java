package fr.siamois.dto.entity;

import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class ActionUnitDTO extends AbstractEntityDTO {

    private String name;
    private ConceptDTO type;
    private String identifier;
    private SpatialUnitSummaryDTO mainLocation ;
    private String fullIdentifier;
    private Set<SpatialUnitSummaryDTO> spatialContext = new HashSet<>();
    private Set<ActionUnitSummaryDTO> children;
    private Set<ActionUnitSummaryDTO> parents;
    private Set<RecordingUnitSummaryDTO> recordingUnitList;
    private Set<Document> documents;
    private OffsetDateTime beginDate;
    private OffsetDateTime endDate;
    private ActionCodeDTO primaryActionCode;
    private int recordingUnitCount;

    private String oaCode;
    private String prescriptionOrderNumber;
    private OffsetDateTime prescriptionOrderDate;
    private String scientificManager;
    private String hostStructure;
    private String developer;
    private Set<ConceptDTO> periods = new HashSet<>();
    private Set<ConceptDTO> subjects = new HashSet<>();
    private String scientificNotice;
    private ConceptDTO status;
    private String comments;
    private ConceptDTO system;
    private ConceptDTO fieldStatus;
    private Double zmin;
    private Double zmax;
    private String designationOrderNumber;
    private OffsetDateTime designationOrderDate;
    private Double prescribedArea;
    private Double excavatedArea;
    private Double accessibleArea;
    private Double openingRate;
    private ConceptDTO developmentNature;
    private Integer volumeCount;
    private Integer pageCount;
    private Integer figureCount;
    private Integer appendixCount;

    public List<String> getBindableFieldNames() {
        return List.of("type", "name", "identifier", "spatialContext", "beginDate", "endDate", "primaryActionCode", "mainLocation",
                "oaCode", "prescriptionOrderNumber", "prescriptionOrderDate", "scientificManager", "hostStructure", "developer",
                "periods", "subjects", "scientificNotice", "status", "comments", "system", "fieldStatus", "zmin", "zmax",
                "designationOrderNumber", "designationOrderDate", "prescribedArea", "excavatedArea", "accessibleArea",
                "openingRate", "developmentNature", "volumeCount", "pageCount", "figureCount", "appendixCount");
    }

    @Override
    public String toString() {
        return name ;
    }

}
