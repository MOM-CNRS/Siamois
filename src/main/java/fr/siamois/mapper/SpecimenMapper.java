package fr.siamois.mapper;

import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        componentModel = MappingConstants.ComponentModel.SPRING)
public interface SpecimenMapper extends Converter<Specimen, SpecimenDTO> {
    @InheritInverseConfiguration
    @DelegatingConverter
    Specimen invertConvert(SpecimenDTO specimenDTO);
}

