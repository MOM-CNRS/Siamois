package fr.siamois.mapper;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.jspecify.annotations.Nullable;
import org.mapstruct.*;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;


@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RecordingUnitMapper extends Converter<RecordingUnit, RecordingUnitDTO> {

    @Override
    @Nullable RecordingUnitDTO convert(@NonNull RecordingUnit source);

    @InheritInverseConfiguration
    @DelegatingConverter
    RecordingUnit invertConvert(RecordingUnitDTO recordingUnitDTO);

}

