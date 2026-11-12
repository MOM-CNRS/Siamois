package fr.siamois.ui.api.openapi.v1.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * The list columns that are no form field, yet are sorted and filtered through the same
 * {@code sort=<fieldId>} / {@code f.<fieldId>} contract as the form fields' ({@link FieldQueryService}).
 *
 * <p>Each one has a reserved id in a block no system field uses ({@code -9xx}; system fields go from
 * {@code -1xx} to {@code -8xx}) and names the entity property it reads. The React lists mirror these
 * ids ({@code frontend/src/panels/list/validationStatusColumn.tsx}).</p>
 */
public enum ListOnlyColumn {

    /** The workflow status every {@link fr.siamois.domain.models.TraceableEntity} carries. */
    VALIDATION_STATUS(-901L, "validationStatus");

    private final long fieldId;
    private final String attribute;

    /**
     * Declares a list-only column.
     *
     * @param fieldId   its reserved id
     * @param attribute the entity property it reads
     */
    ListOnlyColumn(long fieldId, String attribute) {
        this.fieldId = fieldId;
        this.attribute = attribute;
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
     * The entity property the column reads.
     *
     * @return the JPA attribute name
     */
    public String attribute() {
        return attribute;
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
}
