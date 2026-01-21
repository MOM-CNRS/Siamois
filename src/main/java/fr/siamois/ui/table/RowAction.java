package fr.siamois.ui.table;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RowAction {

    private TableColumnAction action;

    /** Optionnel : process PF (ex: "@this") */
    private String processExpr;

    /** Icône (peut être dynamique via clé) */
    private String iconKey;

    /** Condition d’affichage */
    private String renderedKey;



    /** Optionnel : update PF statique (ex: "navBar... bookmarkGroup") */
    private String updateExpr;

    /**
     * Si true, on force l’update de la dataTable du composite :
     * update=":#{cc.clientId}:entityDatatable"
     */
    private boolean updateSelfTable;

    /** CSS optionnelle (ex: "sia-icon-btn") */
    private String styleClass;


}
