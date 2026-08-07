package fr.siamois.dto.entity;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class ContainerDTO extends AbstractEntityDTO {

    protected ConceptDTO type;
    protected SpatialUnitSummaryDTO spatialUnit;
    protected ActionUnitSummaryDTO actionUnit;
    protected Long parentId;
    protected String identifier;
    protected Integer generatedNumber;
    protected MeasurementAnswerDTO length;
    protected MeasurementAnswerDTO width;
    protected MeasurementAnswerDTO height;
    protected MeasurementAnswerDTO weight;

    public static List<String> getBindableFieldNames() {
        return List.of("identifier", "generatedNumber", "type", "spatialUnit", "actionUnit", "parentId", "length", "width", "height", "weight");
    }

}
