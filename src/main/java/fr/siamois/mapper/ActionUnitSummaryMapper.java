package fr.siamois.mapper;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ActionUnitSummaryMapper extends Converter<ActionUnit, ActionUnitSummaryDTO> {

    @InheritInverseConfiguration
    @DelegatingConverter
    ActionUnit invertConvert(ActionUnitSummaryDTO actionUnitSummaryDTO);

}

