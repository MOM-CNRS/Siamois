package fr.siamois.dto;

import lombok.Data;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FilterDTO {

    private final Map<String, FilterInfo> filter;
    public static String GLOBAL_FILTER_KEY = "global";

    public enum FilterType {
        START_WITH,
        CONTAINS,
        EQUAL,
        NOT_STARTS_WITH,
        NOT_CONTAINS,
        NOT_EQUAL
    }

    @Data
    public static class FilterInfo {

        private final Object filter;
        private final FilterType type;

        public FilterInfo(Object filter, FilterType type) {
            this.filter = filter;
            this.type = type;
        }

        public String valueAsString() {
            return (String) filter;
        }
    }

    public FilterDTO() {
        this.filter = new HashMap<>();
    }

    public boolean containsColumn(@NonNull String column) {
        return filter.containsKey(column);
    }

    @NonNull
    public Set<String> getColumns() {
        return filter.keySet();
    }

    @Nullable
    public FilterInfo filterOf(@NonNull String column) {
        return filter.get(column);
    }

    public Object valueOf(@NonNull String column) {
        if (!filter.containsKey(column)) {
            throw new IllegalArgumentException("Column not found: " + column);
        }
        return filter.get(column).filter;
    }

    public String valueOfAsString(@NonNull String column) {
        if (!filter.containsKey(column)) {
            throw new IllegalArgumentException("Column not found: " + column);
        }
        return (String) filter.get(column).filter;
    }

    public void add(String name, Object value, FilterType type) {
        filter.put(name, new FilterInfo(value, type));
    }
}
