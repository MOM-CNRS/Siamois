package fr.siamois.mapper;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.dto.entity.SpatialUnitDTO;
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
public interface SpatialUnitMapper extends Converter<SpatialUnit, SpatialUnitDTO> {

    SpatialUnitDTO convert(@NonNull SpatialUnit source);

    @InheritInverseConfiguration
    @DelegatingConverter
    SpatialUnit invertConvert(SpatialUnitDTO spatialUnitDTO);

}

