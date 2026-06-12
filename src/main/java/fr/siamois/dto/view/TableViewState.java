package fr.siamois.dto.view;

import lombok.Data;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Data
public class TableViewState {

    private int version;

    // layout mode
    private boolean treeMode;

    private boolean columnFilteringEnabled ;

    // column configuration
    private List<ColumnState> columns;

    // sorting rules (multi-sort supported)
    private List<SortState> sorting;

    // filters per column
    private Map<String, FilterState> filters;

    public TableViewState normalize() {

        TableViewState copy = new TableViewState();

        copy.setTreeMode(this.isTreeMode());
        copy.setColumnFilteringEnabled(this.isColumnFilteringEnabled());

        if (this.getColumns() != null) {
            copy.setColumns(
                    this.getColumns().stream()
                            .sorted(Comparator.comparing(ColumnState::getColumnId))
                            .toList()
            );
        }

        if (this.getSorting() != null) {
            copy.setSorting(
                    this.getSorting().stream()
                            .sorted(Comparator.comparing(SortState::getPriority))
                            .toList()
            );
        }

        copy.setFilters(this.getFilters());

        return copy;
    }
}