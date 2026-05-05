package fr.siamois.dto.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;


@Data
public class InstitutionDTO implements Serializable {

        private String name;
        private String description;
        private String identifier;
        private Long id;
        private Set<PersonDTO> managers = new HashSet<>();
        private OffsetDateTime creationDate;

        @Override
        public String toString() {
                return name;
        }
}
