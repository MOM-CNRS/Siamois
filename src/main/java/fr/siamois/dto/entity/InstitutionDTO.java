package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Set;

@EqualsAndHashCode(
        callSuper = true,
        of = {"name", "description", "identifier", "managers"}
)
@Data
public class InstitutionDTO extends AbstractEntityDTO implements Serializable {

        private String name;
        private String description;
        private String identifier;
        private Set<PersonDTO> managers;
}
