package fr.siamois.mapper;

import fr.siamois.domain.models.form.customfield.CustomFieldMeasurement;
import fr.siamois.dto.field.CustomFieldMeasurementDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CustomFieldMeasurementMapper extends Converter<CustomFieldMeasurement, CustomFieldMeasurementDTO> {

    @InheritInverseConfiguration
    @DelegatingConverter
    CustomFieldMeasurement invertConvert(CustomFieldMeasurementDTO customFieldMeasurementDTO);

}

