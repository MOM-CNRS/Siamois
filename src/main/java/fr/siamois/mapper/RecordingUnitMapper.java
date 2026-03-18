package fr.siamois.mapper;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;


@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RecordingUnitMapper extends Converter<RecordingUnit, RecordingUnitDTO> {

    @InheritInverseConfiguration
    @DelegatingConverter
    RecordingUnit invertConvert(RecordingUnitDTO recordingUnitDTO);

}

