package fr.siamois.mapper;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.mapper.adapter.ConversionServiceAdapter;
import org.jspecify.annotations.Nullable;
import org.mapstruct.*;
import org.mapstruct.extensions.spring.DelegatingConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;


@Mapper(uses = ConversionServiceAdapter.class,
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface RecordingUnitMapper extends Converter<RecordingUnit, RecordingUnitDTO> {

    @Override
    @Nullable RecordingUnitDTO convert(@NonNull RecordingUnit source);

    @InheritInverseConfiguration(name="convert")
    @DelegatingConverter
    RecordingUnit invertConvert(RecordingUnitDTO recordingUnitDTO);

    @Mapping(target = "relationshipsAsUnit1", ignore = true)
    @Mapping(target = "relationshipsAsUnit2", ignore = true)
    @Mapping(target = "parents", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "phases", ignore = true)
    RecordingUnitDTO toLightDto(RecordingUnit source);

    /**
     * Conversion pour l'affichage d'une fiche (panneau). Identique à {@link #convert} mais :
     * <ul>
     *   <li>SANS {@code parents}/{@code children} : la hiérarchie est servie par un lazy model
     *       dédié dans le panneau ; cohérent avec {@code RecordingUnitDTO.hierarchyIsInitialized()}
     *       et {@code RecordingUnitService.initializeHierarchy(...)} ;</li>
     *   <li>SANS {@code relationshipsAsUnit1/2} : initialiser ces collections déclenche le batch
     *       fetching des mêmes collections pour toutes les unités voisines en session (~120 ms
     *       constants dès qu'une relation existe). Le service les renseigne lui-même à partir du
     *       résultat du prefetch ({@code prefetchInvolvingRecordingUnitId}, une requête).</li>
     * </ul>
     */
    @Mapping(target = "parents", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "relationshipsAsUnit1", ignore = true)
    @Mapping(target = "relationshipsAsUnit2", ignore = true)
    RecordingUnitDTO toPanelDto(RecordingUnit source);

}

