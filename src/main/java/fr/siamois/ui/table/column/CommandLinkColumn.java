package fr.siamois.ui.table.column;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Column rendering a PrimeFaces commandLink.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CommandLinkColumn extends TableColumn {

    /** What action this link triggers */
    private TableColumnAction action;

    /**
     * Identifier of the value to display.
     * Example: "fullIdentifier"
     * (resolved by the renderer / central handler)
     */
    private String valueKey;

    /** Bootstrap icon class for the entity type (e.g. "bi bi-bucket"). Shown inside the chip. */
    private String iconClass;

    /** CSS color value for the chip background (e.g. "var(--ground-main-color)"). */
    private String chipColor;

    /** Whether the chip text can be edited inline via hover pencil icon. */
    private boolean editable;

    /* Optional PF / JS behaviors */

    private String processExpr;
    private String updateExpr;
    private String onstartJs;
    private String oncompleteJs;

    @Override
    public TableColumnType getType() {
        return TableColumnType.COMMAND_LINK;
    }
}

