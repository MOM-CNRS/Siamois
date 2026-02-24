package fr.siamois.ui.mapper;

import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.ui.form.CustomRowUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(uses = {ColMapper.class})
public interface RowMapper {
    RowMapper INSTANCE = Mappers.getMapper(RowMapper.class);

    CustomRowUiDto customRowToCustomRowUiDto(CustomRow customRow);
}
