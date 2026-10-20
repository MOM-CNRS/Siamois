package fr.siamois.dto.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * What a saved view stores. Version 1 is the JSF shape (columns, sorting, filters); version 2 adds the
 * fields below, which mirror the React list's own {@code TableState} so a view can be applied to it
 * as is. A version 1 view keeps its columns and sort, but its filters are in the JSF shape and not replayed.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableViewState {

    public static final int CURRENT_VERSION = 2;

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

    // version 2: the React list state
    /** Ordered ids of the visible columns. */
    private List<String> visibleColumns;

    /** "field:asc" or "field:desc". */
    private String sort;

    private String search;

    private Integer limit;

    /** "list" or "tree". */
    private String viewMode;

    /** The record type segment selected in a mixed list. */
    private String typeSegment;

    /** Filters by column id, in the React shape ({@code {op:"contains",v}}, {@code {op:"in",v:[]}}, {@code {op:"range",from,to}}). */
    private Map<String, Object> listFilters;

    /** Reserved for views used as the tab of a record page: {@code {label, order}}. */
    private Map<String, Object> tab;

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
        copy.setVersion(this.getVersion());
        copy.setVisibleColumns(this.getVisibleColumns());
        copy.setSort(this.getSort());
        copy.setSearch(this.getSearch());
        copy.setLimit(this.getLimit());
        copy.setViewMode(this.getViewMode());
        copy.setTypeSegment(this.getTypeSegment());
        copy.setListFilters(this.getListFilters());
        copy.setTab(this.getTab());

        return copy;
    }
}