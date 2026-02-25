package fr.siamois.ui.mapper;


import fr.siamois.ui.form.CustomRowUiDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = {ConversionServiceAdapter.class}, componentModel = MappingConstants.ComponentModel.SPRING)
public interface RowMapper extends Converter<RowMapper, CustomRowUiDto> {

}
