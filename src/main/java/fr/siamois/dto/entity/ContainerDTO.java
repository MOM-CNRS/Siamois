package fr.siamois.dto.entity;

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
    protected String identifier;
    protected MeasurementAnswerDTO length;
    protected MeasurementAnswerDTO width;
    protected MeasurementAnswerDTO height;
    protected MeasurementAnswerDTO weight;

    public static List<String> getBindableFieldNames() {
        return List.of("identifier", "type", "spatialUnit", "length", "width", "height", "weight");
    }

}
