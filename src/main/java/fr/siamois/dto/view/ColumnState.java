package fr.siamois.dto.view;

import lombok.Data;

@Data
public class ColumnState {

    /**
     * Must match TableColumn.getId()
     */
    private String columnId;

    private boolean visible;

}
