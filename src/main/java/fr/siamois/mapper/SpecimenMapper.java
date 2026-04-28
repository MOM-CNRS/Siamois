package fr.siamois.mapper;

import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.jspecify.annotations.Nullable;
import org.mapstruct.*;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        componentModel = MappingConstants.ComponentModel.SPRING)
public interface SpecimenMapper extends Converter<Specimen, SpecimenDTO> {


    @Override
    @Nullable SpecimenDTO convert(Specimen source);

    @InheritInverseConfiguration
    @DelegatingConverter
    Specimen invertConvert(SpecimenDTO specimenDTO);
}

