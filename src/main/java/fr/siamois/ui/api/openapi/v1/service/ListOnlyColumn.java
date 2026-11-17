package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * The list columns that are no form field, yet are sorted and filtered through the same
 * {@code sort=<fieldId>} / {@code f.<fieldId>} contract as the form fields' ({@link FieldQueryService}).
 *
 * <p>Each one has a reserved id in a block no system field uses ({@code -9xx}; system fields go from
 * {@code -1xx} to {@code -8xx}) and names the entity property it reads — the same on every entity,
 * unless the entity names it otherwise. An entity that maps no such property cannot be queried on the
 * column (a 400). The React lists mirror these ids ({@code frontend/src/entities/shared/listOnlyColumns.ts}).</p>
 */
public enum ListOnlyColumn {

    /** The workflow status every {@link fr.siamois.domain.models.TraceableEntity} carries. */
    VALIDATION_STATUS(-901L, "validationStatus", Map.of()),

    /** The identifier a list opens the fiche by: the full one where the entity composes it from its parents. */
    IDENTIFIER(-902L, "identifier", Map.of(
            RecordingUnit.class, Constants.FULL_IDENTIFIER,
            Specimen.class, Constants.FULL_IDENTIFIER,
            ActionUnit.class, Constants.FULL_IDENTIFIER)),

    /** The name of the entities that have one (a project). */
    NAME(-903L, "name", Map.of());

    private final long fieldId;
    private final String attribute;
    private final Map<Class<?>, String> attributeByEntity;

    /**
     * Declares a list-only column.
     *
     * @param fieldId           its reserved id
     * @param attribute         the entity property it reads
     * @param attributeByEntity the entities that name that property otherwise
     */
    ListOnlyColumn(long fieldId, String attribute, Map<Class<?>, String> attributeByEntity) {
        this.fieldId = fieldId;
        this.attribute = attribute;
        this.attributeByEntity = attributeByEntity;
    }

    /**
     * The reserved id the column is sorted and filtered by.
     *
     * @return the column's id, negative
     */
    public long fieldId() {
        return fieldId;
    }

    /**
     * The property the column reads on {@code entityType}.
     *
     * @param entityType the listed entity
     * @return the JPA attribute name on that entity
     */
    public String attributeOf(Class<?> entityType) {
        return attributeByEntity.getOrDefault(entityType, attribute);
    }

    /**
     * The list-only column a field id names, if it names one.
     *
     * @param fieldId the id of a {@code sort} or {@code f.} key
     * @return the column, or empty when the id is a form field's
     */
    public static Optional<ListOnlyColumn> ofFieldId(long fieldId) {
        return Arrays.stream(values()).filter(column -> column.fieldId == fieldId).findFirst();
    }

    private static class Constants {
        public static final String FULL_IDENTIFIER = "fullIdentifier";
    }
}
