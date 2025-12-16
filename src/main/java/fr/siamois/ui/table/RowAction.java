package fr.siamois.ui.table;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class RowAction {

    /** Action métier */
    private TableColumnAction action;

    /** Icône (peut être dynamique via clé) */
    private String iconKey;

    /** Condition d’affichage */
    private String renderedKey;

    /** Update / process */
    private String updateExpr;
    private String processExpr;

    /** CSS */
    private String styleClass;
}