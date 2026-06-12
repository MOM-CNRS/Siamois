package fr.siamois.mapper;

import fr.siamois.domain.models.phase.Phase;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PhaseMapper extends Converter<Phase, PhaseDTO> {

    @Override
    PhaseDTO convert(@NonNull Phase source);

    @InheritInverseConfiguration
    @DelegatingConverter
    Phase invertConvert(PhaseDTO phaseDTO);
}
