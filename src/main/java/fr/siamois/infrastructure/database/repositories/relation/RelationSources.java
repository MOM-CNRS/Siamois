package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.study.Study;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Optional;

/** Finds the {@link RelationSource} of a field from its owner and {@code valueBinding}, whichever kind of relation it is. */
public final class RelationSources {

    private static final Map<Class<?>, RelationEntityKind> OWNER_KINDS = Map.of(
            RecordingUnit.class, RelationEntityKind.RECORDING_UNIT,
            Specimen.class, RelationEntityKind.SPECIMEN,
            Phase.class, RelationEntityKind.PHASE,
            Container.class, RelationEntityKind.CONTAINER,
            SpatialUnit.class, RelationEntityKind.SPATIAL_UNIT,
            ActionUnit.class, RelationEntityKind.ACTION_UNIT,
            Document.class, RelationEntityKind.DOCUMENT,
            Study.class, RelationEntityKind.STUDY);

    private RelationSources() {
    }

    /** The kind of entity an owner class is, when a relation can start from it. */
    public static Optional<RelationEntityKind> kindOf(Class<?> ownerType) {
        return Optional.ofNullable(OWNER_KINDS.get(ownerType));
    }

    public static Optional<RelationSource> of(Class<?> ownerType, @Nullable String valueBinding) {
        if (valueBinding == null) return Optional.empty();
        if (DefinitionRelation.isBinding(valueBinding)) {
            return DefinitionRelation.parse(OWNER_KINDS.get(ownerType), valueBinding).map(RelationSource.class::cast);
        }
        return RelationField.of(ownerType, valueBinding).map(RelationSource.class::cast);
    }

    public static boolean isRelation(Class<?> ownerType, @Nullable String valueBinding) {
        return of(ownerType, valueBinding).isPresent();
    }
}
