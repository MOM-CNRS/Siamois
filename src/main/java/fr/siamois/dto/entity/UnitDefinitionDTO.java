package fr.siamois.dto.entity;

import fr.siamois.domain.models.form.measurement.UnitDefinition;

import java.io.Serializable;

public class UnitDefinitionDTO implements Serializable {

    private Long id;
    private ConceptDTO concept;
    private String label;
    private String symbol;
    private UnitDefinition.Dimension dimension;
    private Double factorToBase;
    private boolean systemBase = false;

}
