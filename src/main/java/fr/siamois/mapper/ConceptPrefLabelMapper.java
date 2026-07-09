package fr.siamois.mapper;

import fr.siamois.domain.models.vocabulary.label.ConceptPrefLabel;
import fr.siamois.dto.entity.ConceptPrefLabelDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ConceptPrefLabelMapper extends Converter<ConceptPrefLabel, ConceptPrefLabelDTO> {

    @Mapping(target = "vocabulary", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdByInstitution", ignore = true)
    @Mapping(target = "validated", ignore = true)
    @Mapping(target = "creationTime", ignore = true)
    @Mapping(target = "validatedBy", ignore = true)
    @Mapping(target = "validatedAt", ignore = true)
    ConceptPrefLabelDTO convert(ConceptPrefLabel source);
}
