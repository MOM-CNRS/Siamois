package fr.siamois.infrastructure.database.repositories.specimen;

import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Clause ORDER BY sûre pour les requêtes natives sur {@code specimen s} (pas de tri via {@link Pageable#getSort()}).
 */
// TODO [ARCH] Ne peut on pas réutiliser les méthodes utilisés par les lazy model? A voir avec Julien
public final class SpecimenFindSortSql {

    private static final String DEFAULT_ORDER_BY = "s.creation_time DESC, s.specimen_id ASC";

    private static final Set<String> API_SORT_FIELDS = Set.of("creationTime", "id", "fullIdentifier");

    private SpecimenFindSortSql() {
    }

    public static String fromApiSortParam(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return DEFAULT_ORDER_BY;
        }
        String[] parts = sortParam.split(":", 2);
        String apiField = parts[0].trim();
        if (!API_SORT_FIELDS.contains(apiField)) {
            return DEFAULT_ORDER_BY;
        }
        boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
        return buildOrderBy(apiField, desc);
    }

    public static String fromSpringSort(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return DEFAULT_ORDER_BY;
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String expr = sortPropertyToSql(order.getProperty());
            if (expr == null) {
                continue;
            }
            clauses.add(expr + (order.isDescending() ? " DESC" : " ASC"));
        }
        if (clauses.isEmpty()) {
            return DEFAULT_ORDER_BY;
        }
        if (clauses.stream().noneMatch(c -> c.startsWith("s.specimen_id"))) {
            clauses.add("s.specimen_id ASC");
        }
        return String.join(", ", clauses);
    }

    private static String buildOrderBy(String apiField, boolean desc) {
        String expr = sortPropertyToSql(apiField);
        if (expr == null) {
            return DEFAULT_ORDER_BY;
        }
        String primary = expr + (desc ? " DESC" : " ASC");
        if ("id".equals(apiField)) {
            return primary;
        }
        return primary + ", s.specimen_id ASC";
    }

    private static String sortPropertyToSql(String property) {
        if (property == null || property.isBlank()) {
            return null;
        }
        return switch (property) {
            case "creationTime", "creation_time" -> "s.creation_time";
            case "fullIdentifier", "full_identifier" -> "s.full_identifier";
            case "id", "specimen_id" -> "s.specimen_id";
            case "c_label" -> "c_label";
            default -> null;
        };
    }

    /**
     * Clauses ORDER BY autorisées pour les requêtes natives (whitelist fixe, pas de concaténation utilisateur).
     */
    public enum NativeOrderBy {
        DEFAULT(DEFAULT_ORDER_BY),
        CREATION_TIME_ASC("s.creation_time ASC, s.specimen_id ASC"),
        CREATION_TIME_DESC(DEFAULT_ORDER_BY),
        FULL_IDENTIFIER_ASC("s.full_identifier ASC, s.specimen_id ASC"),
        FULL_IDENTIFIER_DESC("s.full_identifier DESC, s.specimen_id ASC"),
        SPECIMEN_ID_ASC("s.specimen_id ASC"),
        SPECIMEN_ID_DESC("s.specimen_id DESC"),
        LABEL_ASC("c_label ASC, s.specimen_id ASC"),
        LABEL_DESC("c_label DESC, s.specimen_id ASC");

        private final String clause;

        NativeOrderBy(String clause) {
            this.clause = clause;
        }

        public static NativeOrderBy fromClause(String orderByClause) {
            if (orderByClause == null || orderByClause.isBlank()) {
                return DEFAULT;
            }
            String normalized = orderByClause.trim();
            for (NativeOrderBy value : values()) {
                if (value.clause.equalsIgnoreCase(normalized)) {
                    return value;
                }
            }
            return DEFAULT;
        }
    }
}
