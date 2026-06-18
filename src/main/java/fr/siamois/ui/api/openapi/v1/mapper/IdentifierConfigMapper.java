package fr.siamois.ui.api.openapi.v1.mapper;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.api.openapi.v1.resource.type.RecordingUnitIdentifierConfig;
import org.mapstruct.*;
import org.springframework.core.convert.converter.Converter;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IdentifierConfigMapper extends Converter<ActionUnitDTO, RecordingUnitIdentifierConfig> {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "recordingUnitIdentifierFormat", source = "recordingUnitIdentifierFormat")
    @Mapping(target = "recordingUnitIdentifierLang", source = "recordingUnitIdentifierLang")
    @Mapping(target = "maxRecordingUnitCode", source = "maxRecordingUnitCode")
    @Mapping(target = "minRecordingUnitCode", source = "minRecordingUnitCode")
    RecordingUnitIdentifierConfig convert(ActionUnitDTO dto);

}