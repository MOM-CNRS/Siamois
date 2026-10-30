package fr.siamois.mapper.vocabulary;

import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.dto.entity.vocabulary.VocabularyDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface VocabularyMapper extends Converter<Vocabulary, VocabularyDTO> {

    @Override
    @Nullable VocabularyDTO convert(@NonNull Vocabulary source);

    /**
     * The instance is rebuilt from the base URI, unsaved: {@code VocabularyService#saveOrGetVocabulary} resolves it.
     */
    @InheritInverseConfiguration
    @Mapping(target = "instance", ignore = true)
    @DelegatingConverter
    Vocabulary invertConvert(VocabularyDTO source);

}
