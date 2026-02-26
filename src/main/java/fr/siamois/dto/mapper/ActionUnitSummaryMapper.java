package fr.siamois.dto.mapper;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface ActionUnitSummaryMapper extends Converter<ActionUnit, ActionUnitSummaryDTO> {

}

