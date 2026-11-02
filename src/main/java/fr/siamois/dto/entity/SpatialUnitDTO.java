package fr.siamois.dto.entity;

import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.geom.GeometryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class SpatialUnitDTO extends AbstractEntityDTO {

    public SpatialUnitDTO (SpatialUnitDTO spatialUnitDTO) {
        super(spatialUnitDTO);
        id = spatialUnitDTO.getId();
        name = spatialUnitDTO.getName();
        type = spatialUnitDTO.getType();
        parents = spatialUnitDTO.getParents();
        recordingUnitList = spatialUnitDTO.getRecordingUnitList();
        relatedActionUnitList = spatialUnitDTO.relatedActionUnitList;
        address = spatialUnitDTO.address;
        placeNumber = spatialUnitDTO.placeNumber;
        inseeCode = spatialUnitDTO.inseeCode;
        localityName = spatialUnitDTO.localityName;
        addressStreet = spatialUnitDTO.addressStreet;
        addressPostcode = spatialUnitDTO.addressPostcode;
        addressCity = spatialUnitDTO.addressCity;
        parcelSection = spatialUnitDTO.parcelSection;
        parcelNumber = spatialUnitDTO.parcelNumber;
        comments = spatialUnitDTO.comments;
        shomMapNumber = spatialUnitDTO.shomMapNumber;
        geom = spatialUnitDTO.geom;
    }

    private String name;
    private FullAddress address;
    private ConceptDTO type;
    private Set<SpatialUnitSummaryDTO> parents;
    private Set<SpatialUnitSummaryDTO> children;
    private Set<RecordingUnitSummaryDTO> recordingUnitList;
    private Set<ActionUnitSummaryDTO> relatedActionUnitList;
    private Long recordingUnitCount;
    private Integer placeNumber;
    private String inseeCode;
    private String localityName;
    private String addressStreet;
    private String addressPostcode;
    private String addressCity;
    private String parcelSection;
    private String parcelNumber;
    private String comments;
    private String shomMapNumber;
    private GeometryDTO geom;

    public List<String> getBindableFieldNames() {
        return List.of("type", "name", "address", "placeNumber",
                "inseeCode", "localityName", "comments", "shomMapNumber",
                "addressStreet", "addressPostcode", "addressCity", "parcelSection", "parcelNumber");
    }



}
