package fr.siamois.mapper;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface SpecimenMapper extends Converter<Specimen, SpecimenDTO> {

}

