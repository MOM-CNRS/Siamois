package fr.siamois.domain.services;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.dto.entity.*;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class EntityDTORegistry {
    private final Map<Class<? extends AbstractEntityDTO>,
            Class<? extends TraceableEntity>> dtoToEntityMap = new HashMap<>();
    private final Map<Class<? extends AbstractEntityDTO>,
            Class<? extends AbstractEntityDTO>> dtoToummaryMap = new HashMap<>();

    public EntityDTORegistry() {

        dtoToEntityMap.put(RecordingUnitDTO.class, RecordingUnit.class);
        dtoToEntityMap.put(SpecimenDTO.class, Specimen.class);
        dtoToEntityMap.put(ActionUnitDTO.class, ActionUnit.class);
        dtoToEntityMap.put(SpatialUnitDTO.class, SpatialUnit.class);
        dtoToEntityMap.put(RecordingUnitDTO.class, RecordingUnit.class);
        dtoToEntityMap.put(SpecimenDTO.class, Specimen.class);
        dtoToEntityMap.put(ActionUnitDTO.class, ActionUnit.class);
        dtoToEntityMap.put(SpatialUnitDTO.class, SpatialUnit.class);

    }

    @SuppressWarnings("unchecked")
    public <D extends AbstractEntityDTO, E extends TraceableEntity> Class<E> getEntityClass(Class<D> dtoClass) {
        return (Class<E>) dtoToEntityMap.get(dtoClass);
    }

    @SuppressWarnings("unchecked")
    public <D extends AbstractEntityDTO, E extends AbstractEntityDTO> Class<E> getEntitySummaryClass(Class<D> dtoClass) {
        return (Class<E>) dtoToummaryMap.get(dtoClass);
    }
}