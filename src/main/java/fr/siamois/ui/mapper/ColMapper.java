package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.ui.form.CustomColUiDto;
import fr.siamois.ui.form.CustomFormPanelUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;
import org.springframework.core.convert.converter.Converter;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ColMapper extends Converter<CustomCol, CustomColUiDto> {

}

