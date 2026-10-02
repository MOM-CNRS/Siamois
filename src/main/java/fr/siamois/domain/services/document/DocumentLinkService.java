package fr.siamois.domain.services.document;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Links a document to the entities that carry it (recording units, finds, places, phases, containers) and
 * unlinks it. The document owns each link collection, so the operations are idempotent and never touch
 * another kind's links.
 */
@Service
@RequiredArgsConstructor
public class DocumentLinkService {

    private final EntityManager entityManager;
    private final DocumentRepository documentRepository;

    /** Where an entity lives: its institution and, except for a place, its project. */
    public record Target(Long institutionId, Long projectId) {
    }

    @Transactional(readOnly = true)
    public Optional<Target> findTarget(DocumentLinkKind kind, long targetId) {
        Object entity = entityManager.find(kind.entityClass(), targetId);
        if (entity == null) return Optional.empty();
        Long institutionId = entity instanceof TraceableEntity t && t.getCreatedByInstitution() != null
                ? t.getCreatedByInstitution().getId() : null;
        return Optional.of(new Target(institutionId, projectIdOf(entity)));
    }

    private static Long projectIdOf(Object entity) {
        if (entity instanceof RecordingUnit r) return r.getActionUnit() == null ? null : r.getActionUnit().getId();
        if (entity instanceof Specimen s) return s.getActionUnit() == null ? null : s.getActionUnit().getId();
        if (entity instanceof Phase p) return p.getActionUnit() == null ? null : p.getActionUnit().getId();
        if (entity instanceof Container c) return c.getActionUnit() == null ? null : c.getActionUnit().getId();
        return null;
    }

    /** @return {@code true} when the link was added, {@code false} when it already existed */
    @Transactional
    public boolean link(long documentId, DocumentLinkKind kind, long targetId) {
        Document document = requireDocument(documentId);
        Object target = requireTarget(kind, targetId);
        return switch (kind) {
            case RECORDING_UNIT -> document.getRecordingUnits().add((RecordingUnit) target);
            case FIND -> document.getFinds().add((Specimen) target);
            case PLACE -> document.getPlaces().add((SpatialUnit) target);
            case PHASE -> document.getPhases().add((Phase) target);
            case CONTAINER -> document.getContainers().add((Container) target);
        };
    }

    /** @return {@code true} when the link was removed, {@code false} when there was none */
    @Transactional
    public boolean unlink(long documentId, DocumentLinkKind kind, long targetId) {
        Document document = requireDocument(documentId);
        return switch (kind) {
            case RECORDING_UNIT -> document.getRecordingUnits().removeIf(e -> e.getId().equals(targetId));
            case FIND -> document.getFinds().removeIf(e -> e.getId().equals(targetId));
            case PLACE -> document.getPlaces().removeIf(e -> e.getId().equals(targetId));
            case PHASE -> document.getPhases().removeIf(e -> e.getId().equals(targetId));
            case CONTAINER -> document.getContainers().removeIf(e -> e.getId().equals(targetId));
        };
    }

    private Document requireDocument(long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document " + documentId + " not found"));
        if (document.getRecordingUnits() == null || document.getFinds() == null || document.getPlaces() == null
                || document.getPhases() == null || document.getContainers() == null) {
            // A document created earlier in this very transaction still holds the empty collections of the
            // object it was built from: reload it so they are the persistent ones the links are written through.
            entityManager.flush();
            entityManager.refresh(document);
        }
        return document;
    }

    private Object requireTarget(DocumentLinkKind kind, long targetId) {
        Object target = entityManager.find(kind.entityClass(), targetId);
        if (target == null) {
            throw new IllegalArgumentException(kind.pathSegment() + " " + targetId + " not found");
        }
        return target;
    }
}
