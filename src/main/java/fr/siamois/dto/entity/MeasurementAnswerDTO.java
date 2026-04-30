package fr.siamois.dto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementAnswerDTO implements Serializable {

    private Long id;
    private Double numericValue;
    private UnitDefinitionDTO unit;
    private Double normalizedValue;
    private String comment;
}
