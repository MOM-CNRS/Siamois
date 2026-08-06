package fr.siamois.infrastructure.api.dto.concept;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * DTO for the concept field. Structure of the API response for concept fields.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConceptFieldDTO implements Serializable {
    private String uri;
    private String label;
}
