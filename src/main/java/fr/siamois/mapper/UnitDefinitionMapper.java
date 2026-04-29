package fr.siamois.mapper;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UnitDefinitionMapper extends Converter<UnitDefinition, UnitDefinitionDTO> {
    @InheritInverseConfiguration
    @DelegatingConverter
    UnitDefinition invertConvert(UnitDefinitionDTO unitDefinitionDTO);
}

