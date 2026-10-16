package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.infrastructure.database.repositories.PhaseRepository;
import fr.siamois.infrastructure.database.repositories.SpatialUnitRepository;
import fr.siamois.infrastructure.database.repositories.StudyRepository;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.infrastructure.database.repositories.recordingunit.RecordingUnitRepository;
import fr.siamois.infrastructure.database.repositories.specimen.SpecimenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Loads the entity at an end of a link, whatever its kind, with what the write rules need to know about it. */
@Service
@RequiredArgsConstructor
public class RelationEndpointResolver {

    private final RecordingUnitRepository recordingUnitRepository;
    private final SpecimenRepository specimenRepository;
    private final PhaseRepository phaseRepository;
    private final ContainerRepository containerRepository;
    private final SpatialUnitRepository spatialUnitRepository;
    private final ActionUnitRepository actionUnitRepository;
    private final PersonRepository personRepository;
    private final DocumentRepository documentRepository;
    private final StudyRepository studyRepository;

    @Transactional(readOnly = true)
    public Optional<RelationEndpoint> find(RelationEntityKind kind, Long id) {
        if (kind == null || id == null) return Optional.empty();
        return switch (kind) {
            case RECORDING_UNIT -> recordingUnitRepository.findById(id).map(e -> endpoint(kind, e, e.getId(),
                    idOf(e.getActionUnit()), e.getType(), e.getCreatedByInstitution()));
            case SPECIMEN -> specimenRepository.findById(id).map(e -> endpoint(kind, e, e.getId(),
                    specimenProject(e), e.getType(), e.getCreatedByInstitution()));
            case PHASE -> phaseRepository.findById(id).map(e -> endpoint(kind, e, e.getId(),
                    idOf(e.getActionUnit()), e.getType(), e.getCreatedByInstitution()));
            case CONTAINER -> containerRepository.findById(id).map(e -> endpoint(kind, e, e.getId(),
                    idOf(e.getActionUnit()), e.getType(), e.getCreatedByInstitution()));
            case SPATIAL_UNIT -> spatialUnitRepository.findById(id).map(e -> endpoint(kind, e, e.getId(),
                    null, e.getType(), e.getCreatedByInstitution()));
            case ACTION_UNIT -> actionUnitRepository.findById(id).map(e -> endpoint(kind, e, e.getId(),
                    e.getId(), e.getType(), e.getCreatedByInstitution()));
            case PERSON -> personRepository.findById(id).map(e -> endpoint(kind, e, e.getId(), null, null, null));
            case DOCUMENT -> documentRepository.findById(id).map(e -> endpoint(kind, e, e.getId(),
                    idOf(e.getActionUnit()), e.getType(), e.getCreatedByInstitution()));
            case STUDY -> studyRepository.findById(id).map(e -> endpoint(kind, e, e.getId(),
                    idOf(e.getActionUnit()), e.getType(), e.getCreatedByInstitution()));
        };
    }

    private static RelationEndpoint endpoint(RelationEntityKind kind, Object entity, Long id, Long projectId,
                                             Concept type, Institution institution) {
        return new RelationEndpoint(kind, id, entity, projectId, type, institution != null ? institution.getId() : null);
    }

    private static Long idOf(ActionUnit actionUnit) {
        return actionUnit != null ? actionUnit.getId() : null;
    }

    /** A find carries its project directly, or only through its recording unit. */
    private static Long specimenProject(Specimen specimen) {
        if (specimen.getActionUnit() != null) return specimen.getActionUnit().getId();
        RecordingUnit unit = specimen.getRecordingUnit();
        return unit != null ? idOf(unit.getActionUnit()) : null;
    }
}
