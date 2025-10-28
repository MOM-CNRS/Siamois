package fr.siamois.infrastructure.api.mapper;


import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.infrastructure.api.dto.CommuneDTO;

public final class CommuneMapper {
    private CommuneMapper() {}
    public static CommuneDTO toDto(SpatialUnit su) {
        return new CommuneDTO(su.getId(), "00000", su.getName());
    }
}
