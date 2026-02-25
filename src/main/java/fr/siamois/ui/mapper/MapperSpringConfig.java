package fr.siamois.ui.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.extensions.spring.SpringMapperConfig;

@MapperConfig(componentModel = "spring")
@SpringMapperConfig(conversionServiceAdapterPackage = "fr.siamois.ui.mapper.adapter")
public class MapperSpringConfig {
}
