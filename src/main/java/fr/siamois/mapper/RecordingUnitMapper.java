package fr.siamois.mapper;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

import java.util.Set;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface RecordingUnitMapper extends Converter<RecordingUnit, RecordingUnitDTO> {

}

