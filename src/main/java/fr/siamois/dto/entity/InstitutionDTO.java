package fr.siamois.dto.entity;

import lombok.Data;

import java.util.Set;

@Data
public class InstitutionDTO extends AbstractEntityDTO {

        private String name;
        private String description;
        private String identifier;
        private Set<PersonDTO> managers;
}
