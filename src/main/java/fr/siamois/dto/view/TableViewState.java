package fr.siamois.dto.view;

import lombok.Data;

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
}