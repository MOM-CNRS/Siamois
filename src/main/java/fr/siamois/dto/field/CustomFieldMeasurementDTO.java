package fr.siamois.dto.field;


import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import lombok.*;

import java.io.Serializable;

@Setter
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomFieldMeasurementDTO implements Serializable
{
    private UnitDefinitionDTO unit;
    private ConceptDTO measurementNature; // nature
    private ConceptDTO concept; // type
    private boolean isSystemField;
    private String label;
}
