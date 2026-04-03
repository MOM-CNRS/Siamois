package fr.siamois.mapper;


import fr.siamois.dto.PlaceSuggestionDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.*;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PlaceSuggestionMapper extends Converter<SpatialUnitDTO, PlaceSuggestionDTO> {

    @Mapping(target = "sourceName", constant = "SIAMOIS") // Force sourceName to "SIAMOIS"
    PlaceSuggestionDTO convert(SpatialUnitDTO spatialUnitDTO);

    @InheritInverseConfiguration
    @DelegatingConverter
    SpatialUnitDTO invertConvert(PlaceSuggestionDTO placeSuggestionDTO);

}
