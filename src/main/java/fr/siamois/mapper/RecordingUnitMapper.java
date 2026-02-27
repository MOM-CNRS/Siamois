package fr.siamois.mapper;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.*;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

import java.util.Set;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface RecordingUnitMapper extends Converter<RecordingUnit, RecordingUnitDTO> {

    @InheritInverseConfiguration
    @DelegatingConverter
    RecordingUnit invertConvert(RecordingUnitDTO recordingUnitDTO);

}

