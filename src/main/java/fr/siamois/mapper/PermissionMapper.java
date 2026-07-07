package fr.siamois.mapper;

import fr.siamois.domain.models.permissions.Permission;
import fr.siamois.dto.entity.PermissionDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PermissionMapper extends Converter<Permission, PermissionDTO> {

    @Override
    PermissionDTO convert(@NonNull Permission source);

    @InheritInverseConfiguration
    @DelegatingConverter
    Permission invertConvert(PermissionDTO phaseDTO);
}
