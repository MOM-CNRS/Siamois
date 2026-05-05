package fr.siamois.infrastructure.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for the thesaurus. Structure of the API response for thesaurus.
 * @author Julien Linget
 */
@Data
public class ThesaurusDTO {
    private String idTheso;
    private String type;
    private List<LabelDTO> labels;

    public ThesaurusDTO() {
        labels = new ArrayList<>();
    }

    public ThesaurusDTO(String idTheso, List<LabelDTO> labels, String type) {
        this.idTheso = idTheso;
        this.labels = labels;
        this.type = type;
    }

    @JsonIgnore
    private String baseUri;

}
