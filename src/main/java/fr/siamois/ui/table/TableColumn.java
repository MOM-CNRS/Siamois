package fr.siamois.ui.table;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Base abstraction for all table columns.
 * Pure table/UI metadata, no form-field semantics.
 */
@Data
@SuperBuilder
public abstract class TableColumn {

    /** Technical column id (used for toggling, sorting, etc.) */
    private String id;

    /** i18n key for header label */
    private String headerKey;

    /** Is column visible by default */
    private boolean visible;

    /** Is column sortable */
    private boolean sortable;

    /** Sort field key used by LazyDataModel */
    private String sortField;

    /** Is column filterable (handled later) */
    private boolean filterable;

    /** Is column toggleable in column toggler */
    private boolean toggleable;

    /** Optional CSS class */
    private String styleClass;

    /** Optional CSS width (e.g. "150px") */
    private String width;

    /** Column kind discriminator */
    public abstract TableColumnType getType();
}
