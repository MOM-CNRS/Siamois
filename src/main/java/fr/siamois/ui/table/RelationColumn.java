package fr.siamois.ui.table;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * UI column that displays a count of related entities and provides
 * a "view" button/link and an optional "add" button.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RelationColumn extends TableColumn {

    /** i18n header text already in TableColumn.headerKey */

    /** Optional icon for header (e.g. "bi bi-geo-alt") */
    private String headerIcon;

    /** How to compute the count (resolved centrally by the view-model) */
    private String countKey;

    /** View control (button or link) */
    private String viewIcon;              // e.g. "bi bi-eye"
    private TableColumnAction viewAction; // usually VIEW_RELATION
    private Integer viewTargetIndex;      // e.g. 0/2/3 depending on tab/panel. when opening the new panel, which tab index to go

    /** Optional add control */
    private boolean addEnabled;
    private String addIcon;               // e.g. "bi bi-plus-square"
    private TableColumnAction addAction;  // usually ADD_RELATION

    /**
     * Optional "render condition key" evaluated in view-model
     * (e.g. "writeMode", "actionUnitCreateAllowed").
     */
    private String addRenderedKey;

    /** PrimeFaces behaviors */
    private String processExpr;
    private String updateExpr;
    private String onstartJs;
    private String oncompleteJs;

    @Override
    public TableColumnType getType() {
        return TableColumnType.RELATION;
    }
}
