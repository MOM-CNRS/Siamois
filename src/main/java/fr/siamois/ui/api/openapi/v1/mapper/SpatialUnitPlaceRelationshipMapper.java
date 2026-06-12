package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.resource.project.PlaceResourceIdentifier;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface SpatialUnitPlaceRelationshipMapper {

    @Named("spatialUnitToPlaceRelationship")
    default RelationshipToOne<PlaceResourceIdentifier> spatialUnitToPlace(SpatialUnitSummaryDTO su) {
        if (su == null) {
            return null;
        }
        PlaceResourceIdentifier p = new PlaceResourceIdentifier();
        p.setResourceType("places");
        if (su.getId() != null) {
            p.setId(String.valueOf(su.getId()));
        }
        return new RelationshipToOne<>(p);
    }
}
