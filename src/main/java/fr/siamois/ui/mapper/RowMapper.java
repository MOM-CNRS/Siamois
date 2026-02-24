package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.ui.form.CustomFormPanelUiDto;
import fr.siamois.ui.form.CustomRowUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {ColMapper.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface RowMapper extends Converter<RowMapper, CustomRowUiDto> {

}
