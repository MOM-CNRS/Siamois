package fr.siamois.ui.api.openapi.v1.request.list;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The field-keyed part of a list request: {@code sort=<fieldId>:asc|desc},
 * {@code f.<fieldId>[.from|.to]=…} and {@code f.<fieldId>.op=<operator>} ({@link FilterOperator}),
 * where {@code <fieldId>} is a custom field id (negative for a system field). Named keys
 * ({@code sort=fullIdentifier:asc}, {@code f.type=…}) stay with each list's own parser, which skips
 * the numeric ones ({@link #isFieldParameter}).
 *
 * <p>Only the syntax is checked here; what a value means (text, number, date, ids) depends on the
 * field, which {@code FieldQueryService} resolves. {@code lang} is the caller's language, which a
 * sort by concept label reads.</p>
 */
public record FieldListQuery(Map<Long, Criterion> filters, @Nullable Long sortFieldId, boolean ascending, String lang) {

    public static final FieldListQuery NONE = new FieldListQuery(Map.of(), null, true, "fr");

    private static final Pattern FIELD_KEY = Pattern.compile("-?\\d+");
    private static final Pattern FIELD_PARAMETER = Pattern.compile("f\\.-?\\d+(\\.(from|to|op))?");

    /**
     * The raw values sent for one field: {@code values} for a bare key, the bounds for a range, and
     * the operator comparing them — null for the column's own default.
     */
    public record Criterion(List<String> values, @Nullable String from, @Nullable String to,
                            @Nullable FilterOperator operator) {

        public boolean isRange() {
            return from != null || to != null;
        }
    }

    public boolean isEmpty() {
        return filters.isEmpty() && sortFieldId == null;
    }

    /** Whether a filter/sort key names a field by id — i.e. belongs here, not to a named-key parser. */
    public static boolean isFieldKey(String key) {
        return FIELD_KEY.matcher(key).matches();
    }

    /**
     * Whether a whole query parameter ({@code f.-118}, {@code f.42.from}, {@code f.42.op}…) is a
     * field-keyed filter's — i.e. one a named-key parser must leave alone.
     *
     * @param rawKey the parameter name
     * @return true for {@code f.<fieldId>} and its {@code .from}, {@code .to} and {@code .op} suffixes
     */
    public static boolean isFieldParameter(String rawKey) {
        return FIELD_PARAMETER.matcher(rawKey).matches();
    }

    /** Whether {@code sort} orders by a field id. */
    public static boolean isFieldSort(@Nullable String sort) {
        return sort != null && isFieldKey(sortProperty(sort));
    }

    /**
     * {@code sort} for a named-key parser: itself, or {@code fallback} when it orders by a field id
     * (that order comes from the field query instead).
     */
    public static String namedSortOr(@Nullable String sort, String fallback) {
        return isFieldSort(sort) ? fallback : sort;
    }

    public static FieldListQuery parse(@Nullable MultiValueMap<String, String> queryParams, @Nullable String sort,
                                       String lang) {
        Collected collected = new Collected();
        if (queryParams != null) queryParams.forEach(collected::add);
        Map<Long, Criterion> filters = collected.criteria();

        Long sortFieldId = null;
        boolean ascending = true;
        if (isFieldSort(sort)) {
            sortFieldId = Long.parseLong(sortProperty(sort));
            ascending = isAscending(sort);
        }
        return new FieldListQuery(Map.copyOf(filters), sortFieldId, ascending, lang);
    }

    /** The {@code f.<fieldId>[.from|.to|.op]} parameters read so far, by part of the filter. */
    private static final class Collected {

        private final Map<Long, List<String>> values = new LinkedHashMap<>();
        private final Map<Long, String> from = new LinkedHashMap<>();
        private final Map<Long, String> to = new LinkedHashMap<>();
        private final Map<Long, FilterOperator> operators = new LinkedHashMap<>();

        /** One parameter, sorted into the value, lower-bound, upper-bound or operator map; any other ignored. */
        void add(String rawKey, @Nullable List<String> rawValues) {
            if (!isFieldParameter(rawKey)) return;
            String key = rawKey.substring(2);
            int suffix = key.indexOf('.', 1);
            String part = suffix < 0 ? null : key.substring(suffix + 1);
            long fieldId = Long.parseLong(suffix < 0 ? key : key.substring(0, suffix));
            List<String> raw = rawValues == null ? List.of() : rawValues;
            if (part == null) {
                values.computeIfAbsent(fieldId, k -> new ArrayList<>()).addAll(raw);
                return;
            }
            if (raw.size() != 1) throw badRequest("Une seule valeur attendue pour : " + rawKey);
            String value = raw.get(0).trim();
            if (value.isEmpty()) return;
            switch (part) {
                case "from" -> from.put(fieldId, value);
                case "to" -> to.put(fieldId, value);
                default -> operators.put(fieldId, FilterOperator.fromWire(value)
                        .orElseThrow(() -> badRequest("Opérateur inconnu pour " + rawKey + " : " + value)));
            }
        }

        /** One criterion per field named, the blank ones left out; a value and a range together is a 400. */
        Map<Long, Criterion> criteria() {
            Map<Long, Criterion> filters = new LinkedHashMap<>();
            for (Long fieldId : fieldIds()) {
                List<String> v = values.getOrDefault(fieldId, List.of()).stream()
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                boolean range = from.containsKey(fieldId) || to.containsKey(fieldId);
                FilterOperator operator = operators.get(fieldId);
                if (range && !v.isEmpty()) {
                    throw badRequest("Filtre à la fois en valeur et en plage : f." + fieldId);
                }
                boolean takesValues = operator == null || operator.takesValues();
                if (!takesValues && (range || !v.isEmpty())) {
                    throw badRequest("L'opérateur " + operator.wireName() + " ne prend pas de valeur : f." + fieldId);
                }
                // A filter still waiting for its value narrows nothing: dropped, like a blank one.
                if (takesValues && !range && v.isEmpty()) continue;
                filters.put(fieldId, new Criterion(v, from.get(fieldId), to.get(fieldId), operator));
            }
            return filters;
        }

        private Set<Long> fieldIds() {
            Set<Long> fieldIds = new LinkedHashSet<>(values.keySet());
            fieldIds.addAll(from.keySet());
            fieldIds.addAll(to.keySet());
            fieldIds.addAll(operators.keySet());
            return fieldIds;
        }
    }

    private static boolean isAscending(String sort) {
        int colon = sort.lastIndexOf(':');
        String direction = colon < 0 ? "asc" : sort.substring(colon + 1).trim().toLowerCase();
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw badRequest("Direction de tri invalide : " + sort);
        }
        return direction.equals("asc");
    }

    private static String sortProperty(String sort) {
        int colon = sort.lastIndexOf(':');
        return (colon < 0 ? sort : sort.substring(0, colon)).trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
