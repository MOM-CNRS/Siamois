package fr.siamois.mapper;

import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface StatigraphicRelationshipMapper extends Converter<StratigraphicRelationship, StratigraphicRelationshipDTO> {

}

