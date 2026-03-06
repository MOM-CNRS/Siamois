package fr.siamois.mapper;

import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.dto.entity.ActionCodeDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ActionCodeMapper extends Converter<ActionCode, ActionCodeDTO> {

    @InheritInverseConfiguration
    @DelegatingConverter
    ActionCode invertConvert(ActionCodeDTO actionCodeDTO);

}

