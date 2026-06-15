package fr.siamois.ui.api.openapi.v1.generic.mapper;

import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipToOne;
import fr.siamois.ui.api.openapi.v1.generic.response.ResourceIdentifier;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RelationshipToOneMapper extends Converter<ResourceIdentifier,
        RelationshipToOne<ResourceIdentifier>> {

    @Override
    RelationshipToOne<ResourceIdentifier> convert(ResourceIdentifier value) ;

}