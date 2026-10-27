package fr.siamois.mapper;

import fr.siamois.domain.models.document.Document;
import fr.siamois.dto.entity.DocumentSummaryDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.*;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;

@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DocumentSummaryMapper extends Converter<Document, DocumentSummaryDTO> {

    @InheritInverseConfiguration
    @DelegatingConverter
    @Mapping(target = "ark", ignore = true)
    @Mapping(target = "formatConcept", ignore = true)
    @Mapping(target = "storedFileName", ignore = true)
    Document invertConvert(DocumentSummaryDTO documentSummaryDTO);
}
