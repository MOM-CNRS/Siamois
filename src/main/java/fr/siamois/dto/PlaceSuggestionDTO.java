package fr.siamois.dto;

import fr.siamois.dto.entity.ConceptDTO;
import lombok.Data;

@Data
public class PlaceSuggestionDTO {
    private String name;
    private ConceptDTO category;
    private String id;
    private String code;
    private String sourceName; // "SIAMOIS" or sourceName

    public boolean isExternal() {
        return sourceName != null && !"SIAMOIS".equalsIgnoreCase(sourceName);
    }
}
