package fr.siamois.mapper;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.*;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ActionUnitMapper extends Converter<ActionUnit, ActionUnitDTO> {

    @Mapping(target = "documents", ignore = true)
    @Mapping(target = "spatialContext", ignore = true)
    @Mapping(target = "recordingUnitList", ignore = true)
    @Mapping(target = "secondaryActionCodes", ignore = true)
    @Mapping(target = "parents", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Override
    ActionUnitDTO convert(@NonNull ActionUnit source);

    @InheritInverseConfiguration
    @DelegatingConverter
    ActionUnit invertConvert(ActionUnitDTO actionUnitDTO);

}

