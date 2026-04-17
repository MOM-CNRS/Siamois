package fr.siamois.dto.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResultDTO {

    private String matchingTerm;
    private Long actionUnitId;
    private Long spatialUnitId;
    private Long recordingUnitId;
    private Long specimenId;
    
}
