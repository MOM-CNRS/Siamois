package fr.siamois.dto.entity;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class UnitDefinitionDTO implements Serializable {

    private Long id;
    private ConceptDTO concept;
    private String label;
    private String symbol;
    private UnitDefinition.Dimension dimension;
    private Double factorToBase;
    private boolean systemBase = false;

}
