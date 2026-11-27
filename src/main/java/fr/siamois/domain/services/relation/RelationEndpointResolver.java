package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.StudyRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the record at an end of a link, whatever its kind, with what the checks need to know about it, and in
 * particular its project (docs/modele-relations.md, §3.5): the project of a record of a project, the project
 * itself for a project. Places belong to no project and are not accepted for now.
 */
@Service
@RequiredArgsConstructor
public class RelationEndpointResolver {

    private final RecordingUnitRepository recordingUnitRepository;
    private final SpecimenRepository specimenRepository;
    private final PhaseRepository phaseRepository;
    private final ContainerRepository containerRepository;
    private final DocumentRepository documentRepository;
    private final StudyRepository studyRepository;
    private final ActionUnitRepository actionUnitRepository;

    @Transactional(readOnly = true)
    public Optional<RelationEndpoint> find(RelationEndKind kind, Long id) {
        if (kind == null || id == null) return Optional.empty();
        return switch (kind) {
            case RECORDING_UNIT -> recordingUnitRepository.findById(id).map(e -> endpoint(kind, e.getId(), e,
                    idOf(e.getActionUnit()), e.getType()));
            case SPECIMEN -> specimenRepository.findById(id).map(e -> endpoint(kind, e.getId(), e,
                    projectOf(e), e.getType()));
            case PHASE -> phaseRepository.findById(id).map(e -> endpoint(kind, e.getId(), e,
                    idOf(e.getActionUnit()), e.getType()));
            case CONTAINER -> containerRepository.findById(id).map(e -> endpoint(kind, e.getId(), e,
                    idOf(e.getActionUnit()), e.getType()));
            case DOCUMENT -> documentRepository.findById(id).map(e -> endpoint(kind, e.getId(), e,
                    idOf(e.getActionUnit()), e.getType()));
            case STUDY -> studyRepository.findById(id).map(e -> endpoint(kind, e.getId(), e,
                    idOf(e.getActionUnit()), e.getType()));
            case ACTION_UNIT -> actionUnitRepository.findById(id).map(e -> endpoint(kind, e.getId(), e,
                    e.getId(), e.getType()));
            case SPATIAL_UNIT -> Optional.empty();
        };
    }

    /** The records of one kind, by id, in one query: what checking a rule set against existing links reads. */
    @Transactional(readOnly = true)
    public Map<Long, RelationEndpoint> findAll(RelationEndKind kind, Collection<Long> ids) {
        Map<Long, RelationEndpoint> found = new HashMap<>();
        if (ids.isEmpty()) return found;
        switch (kind) {
            case RECORDING_UNIT -> recordingUnitRepository.findAllById(ids).forEach(e -> found.put(e.getId(),
                    endpoint(kind, e.getId(), e, idOf(e.getActionUnit()), e.getType())));
            case SPECIMEN -> specimenRepository.findAllById(ids).forEach(e -> found.put(e.getId(),
                    endpoint(kind, e.getId(), e, projectOf(e), e.getType())));
            case PHASE -> phaseRepository.findAllById(ids).forEach(e -> found.put(e.getId(),
                    endpoint(kind, e.getId(), e, idOf(e.getActionUnit()), e.getType())));
            case CONTAINER -> containerRepository.findAllById(ids).forEach(e -> found.put(e.getId(),
                    endpoint(kind, e.getId(), e, idOf(e.getActionUnit()), e.getType())));
            case DOCUMENT -> documentRepository.findAllById(ids).forEach(e -> found.put(e.getId(),
                    endpoint(kind, e.getId(), e, idOf(e.getActionUnit()), e.getType())));
            case STUDY -> studyRepository.findAllById(ids).forEach(e -> found.put(e.getId(),
                    endpoint(kind, e.getId(), e, idOf(e.getActionUnit()), e.getType())));
            case ACTION_UNIT -> actionUnitRepository.findAllById(ids).forEach(e -> found.put(e.getId(),
                    endpoint(kind, e.getId(), e, e.getId(), e.getType())));
            case SPATIAL_UNIT -> { /* no place is accepted yet */ }
        }
        return found;
    }

    private static RelationEndpoint endpoint(RelationEndKind kind, Long id, TraceableEntity entity, Long projectId,
                                             Concept type) {
        Institution institution = entity.getCreatedByInstitution();
        return new RelationEndpoint(kind, id, entity, projectId, type, institution != null ? institution.getId() : null);
    }

    private static Long idOf(ActionUnit actionUnit) {
        return actionUnit != null ? actionUnit.getId() : null;
    }

    /** A find carries its project directly, or only through its recording unit. */
    private static Long projectOf(Specimen specimen) {
        if (specimen.getActionUnit() != null) return specimen.getActionUnit().getId();
        return specimen.getRecordingUnit() != null ? idOf(specimen.getRecordingUnit().getActionUnit()) : null;
    }
}
