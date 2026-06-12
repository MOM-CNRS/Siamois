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
    private String otherIdentifier;
    private String isolationNumber;
    private ConceptDTO type;
    private ConceptDTO category;
    private List<PersonDTO> authors;
    private List<PersonDTO> collectors;
    private RecordingUnitSummaryDTO recordingUnit;
    protected OffsetDateTime collectionDate;
    private Set<SpecimenSummaryDTO> parents;
    private Set<SpecimenSummaryDTO> children;
    private Set<ConceptDTO> material;
    private Set<ConceptDTO> materialClass;
    private ConceptDTO normalizedInterpretation;
    private ConceptDTO chronologicalAttribution;
    private String description;
    private String comments;
    private Integer taq;
    private Integer tpq;
    private Integer numberOfElements;
    private MeasurementAnswerDTO weight;
    private Set<ContainerDTO> containers;
    private Set<PhaseDTO> phases;

    public SpecimenDTO(SpecimenDTO original) {

    }

    public static List<String> getBindableFieldNames() {
        return List.of(
                "recordingUnit",
                "parents",
                "children",
                "fullIdentifier",
                "otherIdentifier",
                "isolationNumber",
                "authors",
                "collectors",
                "category",
                "collectionDate",
                "material",
                "materialClass",
                "normalizedInterpretation",
                "description",
                "comments",
                "chronologicalAttribution",
                "taq",
                "tpq",
                "numberOfElements",
                "weight",
                "containers",
                "phases",
                "type"
        );
    }
}