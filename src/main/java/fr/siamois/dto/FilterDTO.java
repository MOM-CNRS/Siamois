package fr.siamois.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

public class FilterDTO {

    private final Map<String, FilterInfo> filter;
    public static String GLOBAL_FILTER_KEY = "global";

    /**
     * Set to true if the filter specifies that the request should only return roots.
     */
    @Setter
    @Getter
    private boolean rootOnly;

    /**
     * Set by services when {@code rootOnly} is active and user filters are non-empty:
     * union of (matches ∪ all their ancestors). The lazy model reads it after the call
     * to constrain child-loading to the same closure (auto-expand branches that lead
     * to a match). {@code null} otherwise.
     */
    @Setter
    @Getter
    private Collection<Long> ancestorClosure;

    /**
     * Set alongside {@link #ancestorClosure}: ids of the entities that actually
     * matched the user filter (without their ancestors). Used by the tree to
     * decide whether closure-based child filtering still applies — when the
     * parent IS a match, all its descendants belong to the result and the
     * closure filter must be lifted.
     */
    @Setter
    @Getter
    private Set<Long> matchIds;

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
        rootOnly = false;
        this.filter = new HashMap<>();
    }

    public FilterDTO(boolean rootOnly) {
        this.rootOnly = rootOnly;
        this.filter = new HashMap<>();
    }

    public boolean containsColumn(@NonNull String column) {
        return filter.containsKey(column);
    }

    public boolean hasUserFilters() {
        return !filter.isEmpty();
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

    @SuppressWarnings("unchecked")
    public List<Long> valueAsIdListOf(@NonNull String column) {
        if (!filter.containsKey(column)) {
            throw new IllegalArgumentException("Column not found: " + column);
        }
        return (List<Long>) filter.get(column).filter;
    }

    public DateRange valueAsDateRangeOf(@NonNull String column) {
        if (!filter.containsKey(column)) {
            throw new IllegalArgumentException("Column not found: " + column);
        }
        Object raw = filter.get(column).filter;
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return new DateRange(null, null);
        }
        OffsetDateTime from = toOffsetDateTime(list.get(0));
        OffsetDateTime to = list.size() > 1 ? toOffsetDateTime(list.get(1)) : null;
        return new DateRange(from, to);
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof Date date) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }

        if (value instanceof LocalDate date) {
            return date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        }

        throw new IllegalArgumentException("Unsupported date value: " + value.getClass());
    }

    public record DateRange(OffsetDateTime from, OffsetDateTime to) {}

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
