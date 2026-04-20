package fr.siamois.mapper;

import fr.siamois.domain.models.form.measurement.MeasurementAnswer;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface MeasurementAnswerMapper extends Converter<MeasurementAnswer, MeasurementAnswerDTO> {
    @InheritInverseConfiguration
    @DelegatingConverter
    MeasurementAnswer invertConvert(MeasurementAnswerDTO measurementAnswerDTO);
}

