package fr.siamois.ui.table;

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

