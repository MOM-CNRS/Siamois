package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.ui.form.CustomFormPanelUiDto;
import fr.siamois.ui.form.FormUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {RowMapper.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface FormPanelMapper extends Converter<CustomFormPanel, CustomFormPanelUiDto> {

}

