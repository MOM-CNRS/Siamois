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
public interface RecordingUnitResponseMapper extends Converter<RecordingUnitDTO,
        RecordingUnitResource> {

    @Mapping(target = "resourceType", constant = "recording-unit")
    @Mapping(target = "resourceId", constant = "id")
    @Mapping(target = "specimen", source = "specimenCount")
    RecordingUnitResource convert(RecordingUnitDTO recordingUnitDTO);

}
