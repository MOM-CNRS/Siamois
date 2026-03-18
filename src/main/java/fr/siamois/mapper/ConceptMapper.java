package fr.siamois.mapper;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ConceptMapper extends Converter<Concept, ConceptDTO> {

    @InheritInverseConfiguration
    @DelegatingConverter
    Concept invertConvert(ConceptDTO conceptDTO);

}

