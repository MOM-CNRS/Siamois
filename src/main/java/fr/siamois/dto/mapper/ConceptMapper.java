package fr.siamois.dto.mapper;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ConceptMapper extends Converter<Concept, ConceptDTO> {

}

