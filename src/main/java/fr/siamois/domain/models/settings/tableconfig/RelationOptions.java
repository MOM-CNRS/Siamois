package fr.siamois.domain.models.settings.tableconfig;

import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.RelationMirror;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * What a relation field carries besides its name: the kind of entity it links to, the config types each side
 * accepts (empty = any), whether the other side is mirrored by a field of its own, and whether a link can be
 * flagged uncertain. The kind, the mirror and the link form are fixed when the field is created.
 *
 * @param targetKind  the kind of entity the field lists
 * @param sourceTypes the types of the entity that carries the field accepted by the relation
 * @param targetTypes the types of the linked entity accepted by the relation
 * @param mirror      whether the relation has no mirror, is symmetric, or is mirrored by another field
 * @param mirrorLabel the name of the mirror field (only for {@link RelationMirror#OTHER})
 * @param uncertain   whether a link carries an "uncertain" flag
 * @param mirrorFieldId an existing field of the linked entity to use as the mirror, instead of creating one
 *                    (only when the relation is created, for {@link RelationMirror#OTHER})
 */
public record RelationOptions(RelationEntityKind targetKind, List<Long> sourceTypes, List<Long> targetTypes,
                              RelationMirror mirror, @Nullable String mirrorLabel, boolean uncertain,
                              @Nullable Long mirrorFieldId)
        implements Serializable {

    public RelationOptions(RelationEntityKind targetKind, List<Long> sourceTypes, List<Long> targetTypes,
                           RelationMirror mirror, @Nullable String mirrorLabel, boolean uncertain) {
        this(targetKind, sourceTypes, targetTypes, mirror, mirrorLabel, uncertain, null);
    }

    public RelationOptions {
        sourceTypes = sourceTypes == null ? List.of() : List.copyOf(sourceTypes);
        targetTypes = targetTypes == null ? List.of() : List.copyOf(targetTypes);
    }
}
