package fr.siamois.mapper;

import fr.siamois.domain.models.study.Study;
import fr.siamois.dto.entity.StudyDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

/**
 * Study ↔ DTO. The links to recording units and finds travel as summaries; the service resolves them
 * back to entities when it saves, so the inverse leaves them alone.
 */
@Mapper(uses = ConversionServiceAdapter.class, componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StudyMapper extends Converter<Study, StudyDTO> {

    @Override
    StudyDTO convert(@NonNull Study source);

    @InheritInverseConfiguration
    @Mapping(target = "recordingUnits", ignore = true)
    @Mapping(target = "specimens", ignore = true)
    @DelegatingConverter
    Study invertConvert(StudyDTO dto);
}
