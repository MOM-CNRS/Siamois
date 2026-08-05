package fr.siamois.dto.field;


import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
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
