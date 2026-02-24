package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.ui.form.FormUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {FormPanelMapper.class})
public interface FormMapper {
    FormMapper INSTANCE = Mappers.getMapper(FormMapper.class);

    FormUiDto customFormToFormUiDto(CustomForm customForm);
}

