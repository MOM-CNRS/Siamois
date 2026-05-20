package fr.siamois.mapper;

import fr.siamois.domain.models.container.Container;
import fr.siamois.dto.entity.ContainerDTO;
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
public interface ContainerMapper extends Converter<Container, ContainerDTO> {

    @Override
    ContainerDTO convert(@NonNull Container source);

    @InheritInverseConfiguration
    @DelegatingConverter
    Container invertConvert(ContainerDTO containerDTO);

}

