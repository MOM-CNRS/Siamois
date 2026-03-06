package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.ui.api.openapi.v1.resource.recordingunit.*;
import fr.siamois.dto.entity.RecordingUnitDTO;

import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {
        ConversionServiceAdapter.class,
        ConceptResourceIdentifierMapper.class,
        PersonResourceIdentifierMapper.class
},
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RecordingUnitResourceMapper extends Converter<RecordingUnitDTO,
        RecordingUnitResource> {

    @Mapping(target = "type", constant = "recording-unit")
    @Mapping(target = "id", source = "id")
    @Mapping(target = "attributes", source = ".")
    @Mapping(target = "relationships", source = ".")
    RecordingUnitResource convert(RecordingUnitDTO recordingUnitDTO);


}
