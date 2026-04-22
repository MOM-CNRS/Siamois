package fr.siamois.dto.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class MeasurementAnswerDTO implements Serializable {

    private Long id;
    private Double numericValue;
    private UnitDefinitionDTO unit;
    private Double normalizedValue;
    private String comment;
}
