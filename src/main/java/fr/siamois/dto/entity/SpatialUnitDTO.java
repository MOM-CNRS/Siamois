package fr.siamois.dto.entity;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class SpatialUnitDTO extends AbstractEntityDTO {

    public SpatialUnitDTO (SpatialUnitDTO spatialUnitDTO) {
        super(spatialUnitDTO);
        id = spatialUnitDTO.getId();
        name = spatialUnitDTO.getName();
        category = spatialUnitDTO.getCategory();
        parents = spatialUnitDTO.getParents();
        recordingUnitList = spatialUnitDTO.getRecordingUnitList();
        relatedActionUnitList = spatialUnitDTO.relatedActionUnitList;
        address = spatialUnitDTO.address;
        placeNumber = spatialUnitDTO.placeNumber;
        geom = spatialUnitDTO.geom;
    }

    private String name;
    private FullAddress address;
    private ConceptDTO category;
    private Set<SpatialUnitSummaryDTO> parents;
    private Set<SpatialUnitSummaryDTO> children;
    private Set<RecordingUnitSummaryDTO> recordingUnitList;
    private Set<ActionUnitSummaryDTO> relatedActionUnitList;
    private Long recordingUnitCount;
    private String code;
    private Integer placeNumber;
    private GeometryDTO geom;

    public List<String> getBindableFieldNames() {
        return List.of("category", "name", "address", "code", "placeNumber");
    }



}
