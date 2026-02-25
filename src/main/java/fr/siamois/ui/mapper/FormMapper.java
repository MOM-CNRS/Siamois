package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.ui.form.FormUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
@Component
public interface FormMapper extends Converter<CustomForm, FormUiDto> {

}

