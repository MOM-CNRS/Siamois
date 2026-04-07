package fr.siamois.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.siamois.dto.entity.ConceptDTO;
import lombok.Data;

import java.io.Serializable;

@Data
public class PlaceSuggestionDTO implements Serializable {
    private String name;
    private ConceptDTO category;
    private Long id;
    private String code;
    private String sourceName; // "SIAMOIS" or sourceName

    @JsonIgnore
    public boolean isExternal() {
        return sourceName != null && !"SIAMOIS".equalsIgnoreCase(sourceName);
    }
}
