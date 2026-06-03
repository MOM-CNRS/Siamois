package fr.siamois.mapper;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.*;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ActionUnitMapper extends Converter<ActionUnit, ActionUnitDTO> {

    @Override
    @Mapping(target = "recordingUnitList", ignore = true)
    ActionUnitDTO convert(@NonNull ActionUnit source);

    @InheritInverseConfiguration(name="convert")
    @DelegatingConverter
    ActionUnit invertConvert(ActionUnitDTO actionUnitDTO);

    @Mapping(target = "recordingUnitList", ignore = true)
    ActionUnitDTO toLightDto(ActionUnit source);

}

