package fr.siamois.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.extensions.spring.SpringMapperConfig;
/**
 * @author felord.cn
 * @since 1.0.0
 */
@MapperConfig(componentModel = "spring")
@SpringMapperConfig(conversionServiceAdapterPackage = "fr.siamois.ui.mapper.adapter")
public class MapperSpringConfig {
}
