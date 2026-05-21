package fr.siamois.mapper;


import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.dto.entity.ActionCodeDTO;
import fr.siamois.dto.view.UITableViewDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.*;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UITableViewMapper extends Converter<UiTableView, UITableViewDTO> {

    UITableViewDTO toDto(UiTableView entity);

    UiTableView toEntity(UITableViewDTO dto);

}
