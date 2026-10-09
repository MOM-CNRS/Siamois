package fr.siamois.dto.entity;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
    private List<PersonDTO> authors;
    private List<PersonDTO> collectors;
    private RecordingUnitSummaryDTO recordingUnit;
    private ActionUnitSummaryDTO actionUnit;
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

    /**
     * A new, unsaved find with the descriptive data of {@code original}: what a duplication starts
     * from. Not copied: the id, the identifiers (regenerated on save; the other identifier and the
     * isolation number name one particular find), the hierarchy (parents, children), where it is kept
     * (containers) and its phases. Collections are copied, not shared, and so is the weight, so
     * the copy owns its own measurement.
     */
    public SpecimenDTO(SpecimenDTO original) {
        createdByInstitution = original.getCreatedByInstitution();
        type = original.getType();
        recordingUnit = original.getRecordingUnit();
        actionUnit = original.getActionUnit();
        authors = original.getAuthors() == null ? null : new ArrayList<>(original.getAuthors());
        collectors = original.getCollectors() == null ? null : new ArrayList<>(original.getCollectors());
        collectionDate = original.getCollectionDate();
        material = original.getMaterial() == null ? null : new HashSet<>(original.getMaterial());
        materialClass = original.getMaterialClass() == null ? null : new HashSet<>(original.getMaterialClass());
        normalizedInterpretation = original.getNormalizedInterpretation();
        chronologicalAttribution = original.getChronologicalAttribution();
        description = original.getDescription();
        comments = original.getComments();
        taq = original.getTaq();
        tpq = original.getTpq();
        numberOfElements = original.getNumberOfElements();
        if (original.getWeight() != null) {
            MeasurementAnswerDTO w = original.getWeight();
            weight = new MeasurementAnswerDTO(null, w.getNumericValue(), w.getUnit(), w.getNormalizedValue(), w.getComment());
        }
    }

    public static List<String> getBindableFieldNames() {
        return List.of(
                "recordingUnit",
                "actionUnit",
                "parents",
                "children",
                "fullIdentifier",
                "otherIdentifier",
                "isolationNumber",
                "authors",
                "collectors",
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