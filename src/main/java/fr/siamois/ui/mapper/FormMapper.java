package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.ui.form.FormUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {FormPanelMapper.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface FormMapper extends Converter<CustomForm, FormUiDto> {
    FormUiDto customFormToFormUiDto(CustomForm customForm);
}

