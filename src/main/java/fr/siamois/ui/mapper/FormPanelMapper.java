package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.ui.form.CustomFormPanelUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {RowMapper.class})
public interface FormPanelMapper {
    FormPanelMapper INSTANCE = Mappers.getMapper(FormPanelMapper.class);

    CustomFormPanelUiDto customFormPanelToCustomFormPanelUiDto(CustomFormPanel customFormPanel);
}

