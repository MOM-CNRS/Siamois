package fr.siamois.dto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Set;


@Data
@EqualsAndHashCode(callSuper = true)
public class InstitutionDTO extends AbstractEntityDTO implements Serializable {

        private String name;
        private String description;
        private String identifier;
        private Set<PersonDTO> managers;
        private OffsetDateTime creationDate;
}
