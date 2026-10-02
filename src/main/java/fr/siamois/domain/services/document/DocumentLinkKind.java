package fr.siamois.domain.services.document;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.infrastructure.database.repositories.specs.DocumentSpec;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * The entities a document can be linked to besides its project. Each has its own link table (the
 * document owns the collection), so linking one kind never touches the links of another.
 */
public enum DocumentLinkKind {
    RECORDING_UNIT("recording-units", RecordingUnit.class, DocumentSpec::linkedToRecordingUnit),
    FIND("finds", Specimen.class, DocumentSpec::linkedToFind),
    PLACE("places", SpatialUnit.class, DocumentSpec::linkedToPlace),
    PHASE("phases", Phase.class, DocumentSpec::linkedToPhase),
    CONTAINER("containers", Container.class, DocumentSpec::linkedToContainer);

    private final String pathSegment;
    private final Class<?> entityClass;
    private final Function<Long, Specification<Document>> linkedTo;

    DocumentLinkKind(String pathSegment, Class<?> entityClass, Function<Long, Specification<Document>> linkedTo) {
        this.pathSegment = pathSegment;
        this.entityClass = entityClass;
        this.linkedTo = linkedTo;
    }

    /** The REST collection of the entity ({@code /api/v1/<segment>/{id}/documents}). */
    public String pathSegment() {
        return pathSegment;
    }

    public Class<?> entityClass() {
        return entityClass;
    }

    /** The documents linked to the entity {@code targetId}. */
    public Specification<Document> documentsLinkedTo(long targetId) {
        return linkedTo.apply(targetId);
    }

    public static Optional<DocumentLinkKind> ofPathSegment(String segment) {
        return Arrays.stream(values()).filter(k -> k.pathSegment.equals(segment)).findFirst();
    }
}
