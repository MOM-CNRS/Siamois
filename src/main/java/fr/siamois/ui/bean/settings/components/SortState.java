package fr.siamois.ui.bean.settings.components;

import lombok.Getter;

import java.io.Serializable;

/**
 * Which column a settings list is sorted on, and in which direction. The lists sort themselves (their
 * bean holds the comparators), the headers are plain links that call {@link #toggle}: a first click sorts
 * ascending, a second descending, a third goes back to ascending.
 */
@Getter
public class SortState implements Serializable {

    private String key;
    private boolean ascending = true;

    public SortState(String defaultKey) {
        this.key = defaultKey;
    }

    public void toggle(String column) {
        if (column.equals(key)) {
            ascending = !ascending;
        } else {
            key = column;
            ascending = true;
        }
    }

    /** The icon of a column's header: its direction when it is the sorted one, a neutral one otherwise. */
    public String iconOf(String column) {
        if (!column.equals(key)) {
            return "bi-chevron-expand";
        }
        return ascending ? "bi-sort-up" : "bi-sort-down";
    }

    public boolean isSorted(String column) {
        return column.equals(key);
    }
}
