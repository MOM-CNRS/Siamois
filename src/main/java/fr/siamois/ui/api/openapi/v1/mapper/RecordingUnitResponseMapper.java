package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitResource;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RecordingUnitResponseMapper extends Converter<RecordingUnitDTO, RecordingUnitResource> {

    @Mapping(target = "resourceType", constant = "recording-units")
    @Mapping(target = "id", expression = "java(String.valueOf(dto.getId()))")
    @Mapping(target = "projectId", expression = "java(dto.getActionUnit() != null ? String.valueOf(dto.getActionUnit().getId()) : null)")
    @Mapping(target = "typeId", expression = "java(dto.getType() != null ? String.valueOf(dto.getType().getId()) : null)")
    @Mapping(target = "geom", ignore = true)
    @Mapping(target = "answers", ignore = true)
    RecordingUnitResource convert(RecordingUnitDTO dto);
}
