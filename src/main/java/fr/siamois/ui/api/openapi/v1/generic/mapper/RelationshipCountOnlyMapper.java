package fr.siamois.ui.api.openapi.v1.generic.mapper;

import fr.siamois.ui.api.openapi.v1.generic.response.RelationshipCountOnly;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RelationshipCountOnlyMapper
        extends Converter<Long, RelationshipCountOnly> {

    default RelationshipCountOnly convert(Long count) {
        return new RelationshipCountOnly(count);
    }

}