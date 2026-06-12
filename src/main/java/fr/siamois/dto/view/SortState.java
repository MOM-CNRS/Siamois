package fr.siamois.dto.view;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializable representation of a column sort rule
 * inside a saved table view.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortState {

    /**
     * Column identifier (must match TableColumn.getId()).
     */
    private String columnId;

    /**
     * Sort direction.
     */
    private Direction direction;

    /**
     * Sort priority (for multi-column sorting).
     * Lower number = higher priority.
     */
    private Integer priority;

    public enum Direction {
        ASC,
        DESC
    }
}
