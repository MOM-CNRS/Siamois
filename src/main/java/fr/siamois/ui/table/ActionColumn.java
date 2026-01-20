package fr.siamois.ui.table;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Data
@EqualsAndHashCode(callSuper = true)
public class ActionColumn extends TableColumn {

    /** Liste ordonnée des actions disponibles */
    private List<RowAction> actions;

    @Override
    public TableColumnType getType() {
        return TableColumnType.ACTIONS;
    }
}
