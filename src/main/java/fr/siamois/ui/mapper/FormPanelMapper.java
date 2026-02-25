package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.ui.form.CustomFormPanelUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface FormPanelMapper extends Converter<CustomFormPanel, CustomFormPanelUiDto> {

}

