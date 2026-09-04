package fr.siamois.domain.services;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.domain.services.spatialunit.SpatialUnitService;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceInstitutionService {

    private final SpatialUnitService spatialUnitService;
    private final ActionUnitService actionUnitService;
    private final RecordingUnitService recordingUnitService;
    private final SpecimenService specimenService;
    private final ContainerService containerService;
    private final PhaseService phaseService;

    /**
     * Finds the institution owning the entity of the given resource type and id.
     *
     * @param resourceType the resource type of the panel, e.g. {@code spatial-unit}
     * @param entityId     the id of the entity; null for a list panel, which belongs to no entity
     * @return the owning institution, or empty when the resource has no entity, when the entity does not
     * exist or when it has no institution
     */
    public Optional<InstitutionDTO> findInstitutionOf(String resourceType, Long entityId) {
        if (resourceType == null || entityId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(findEntity(resourceType, entityId))
                .map(AbstractEntityDTO::getCreatedByInstitution);
    }

    private AbstractEntityDTO findEntity(String resourceType, long entityId) {
        try {
            return switch (resourceType) {
                case "spatial-unit" -> spatialUnitService.findById(entityId);
                case "action-unit" -> actionUnitService.findById(entityId);
                case "recording-unit" -> recordingUnitService.findById(entityId);
                case "specimen" -> specimenService.findById(entityId);
                case "container" -> containerService.findById(entityId);
                case "phase" -> phaseService.findById(entityId);
                default -> null;
            };
        } catch (RuntimeException e) {
            // A missing entity is reported by the panel itself; here it simply has no institution.
            log.warn("Could not resolve the institution of {}/{}: {}", resourceType, entityId, e.getMessage());
            return null;
        }
    }

}
