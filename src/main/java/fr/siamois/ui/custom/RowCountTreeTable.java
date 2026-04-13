package fr.siamois.ui.custom;

import lombok.Getter;
import lombok.Setter;
import org.primefaces.component.treetable.TreeTable;

public class RowCountTreeTable extends TreeTable {

    @Getter
    @Setter
    private int rowCount;

    @Override
    public int getRows() {
        return super.getRows();
    }

    @Override
    public int getPageCount() {
        int rowsPerPage = getRows();
        if (rowsPerPage <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) rowCount / rowsPerPage);
    }
}
