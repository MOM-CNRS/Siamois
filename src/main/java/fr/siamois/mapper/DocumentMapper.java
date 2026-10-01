package fr.siamois.mapper;

import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DocumentMapper extends Converter<Document, DocumentDTO> {

    @Override
    DocumentDTO convert(@NonNull Document source);

    @InheritInverseConfiguration
    @DelegatingConverter
    @Mapping(target = "ark", ignore = true)
    @Mapping(target = "formatConcept", ignore = true)
    @Mapping(target = "storedFileName", ignore = true)
    Document invertConvert(DocumentDTO documentDTO);
}
