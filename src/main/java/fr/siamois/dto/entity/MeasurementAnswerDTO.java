package fr.siamois.dto.entity;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import jakarta.persistence.*;

import java.io.Serializable;

public class MeasurementAnswerDTO implements Serializable {

    private Long id;
    private Double numericValue;
    private UnitDefinitionDTO unit;
    private Double normalizedValue;
    private String comment;
}
