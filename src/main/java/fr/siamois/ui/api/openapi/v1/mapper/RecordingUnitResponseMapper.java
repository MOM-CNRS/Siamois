package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.RecordingUnitDTO;

import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {
        ConversionServiceAdapter.class,
        ConceptResourceIdentifierMapper.class,
        PersonResourceIdentifierMapper.class,
        SpatialUnitPlaceRelationshipMapper.class
},
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RecordingUnitResponseMapper extends Converter<RecordingUnitDTO,
        RecordingUnitResource> {

    @Mapping(target = "resourceType", constant = "recording-units")
    @Mapping(target = "author", source = "author", qualifiedByName = "toAuthorRelationship")
    @Mapping(target = "place", source = "spatialUnit", qualifiedByName = "spatialUnitToPlaceRelationship")
    RecordingUnitResource convert(RecordingUnitDTO recordingUnitDTO);

}
