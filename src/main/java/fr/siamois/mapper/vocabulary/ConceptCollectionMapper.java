package fr.siamois.mapper.vocabulary;

import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.dto.entity.vocabulary.ConceptCollectionDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ConceptCollectionMapper extends Converter<ConceptCollection, ConceptCollectionDTO> {

    @Override
    @Nullable ConceptCollectionDTO convert(@NonNull ConceptCollection source);

    @InheritInverseConfiguration
    @DelegatingConverter
    ConceptCollection invertConvert(ConceptCollectionDTO source);
}
