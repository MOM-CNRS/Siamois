package fr.siamois.ui.api.openapi.v1.jsonapi;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RelationshipMapper extends Converter<ResourceIdentifier,
        Relationship<ResourceIdentifier>> {

    @Override
    @Mapping(target = "data", source = ".")
    Relationship<ResourceIdentifier> convert(ResourceIdentifier value) ;

}