package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.ui.form.CustomColUiDto;
import org.mapstruct.factory.Mappers;

public interface ColMapper {
    ColMapper INSTANCE = Mappers.getMapper(ColMapper.class);

    CustomColUiDto customColToCustomColUiDto(CustomCol customCol);
}

