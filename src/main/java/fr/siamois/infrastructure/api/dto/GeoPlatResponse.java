package fr.siamois.infrastructure.api.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;


@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GeoPlatResponse {
    private String status;
    private List<GeoPlatResult> results;
}
