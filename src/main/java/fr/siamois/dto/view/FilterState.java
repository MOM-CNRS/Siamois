package fr.siamois.dto.view;


import lombok.Data;

@Data
public class FilterState {

    private String columnId;

    private FilterType type;

    /**
     * Generic value container:
     * - String for text
     * - List<String> for multi-select IDs
     * - Map for ranges
     */
    private Object value;

    public enum FilterType {
        TEXT,
        NUMBER,
        DATE,
        DATE_RANGE,
        CONCEPT,
        PERSON,
        ACTION_UNIT,
        SPATIAL_UNIT,
        BOOLEAN
    }
}