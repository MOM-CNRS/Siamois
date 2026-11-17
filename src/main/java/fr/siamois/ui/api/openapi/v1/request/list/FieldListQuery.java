package fr.siamois.ui.api.openapi.v1.request.list;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The field-keyed part of a list request: {@code sort=<fieldId>:asc|desc},
 * {@code f.<fieldId>[.from|.to]=…} and {@code f.<fieldId>.op=<operator>} ({@link FilterOperator}),
 * where {@code <fieldId>} is a custom field id (negative for a system field). Named keys ({@code sort=fullIdentifier:asc}, {@code f.type=…}) stay with each
 * list's own parser, which skips the numeric ones ({@link #isFieldKey}).
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

        public Criterion(List<String> values, @Nullable String from, @Nullable String to) {
            this(values, from, to, null);
        }

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
        Map<Long, List<String>> values = new LinkedHashMap<>();
        Map<Long, String> from = new LinkedHashMap<>();
        Map<Long, String> to = new LinkedHashMap<>();
        Map<Long, FilterOperator> operators = new LinkedHashMap<>();
        if (queryParams != null) {
            for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
                collect(entry.getKey(), entry.getValue(), new Collected(values, from, to, operators));
            }
        }
        Map<Long, Criterion> filters = criteria(new Collected(values, from, to, operators));

        Long sortFieldId = null;
        boolean ascending = true;
        if (isFieldSort(sort)) {
            sortFieldId = Long.parseLong(sortProperty(sort));
            ascending = isAscending(sort);
        }
        return new FieldListQuery(Map.copyOf(filters), sortFieldId, ascending, lang);
    }

    /** The parameters read so far, by part of an {@code f.<fieldId>} filter. */
    private record Collected(Map<Long, List<String>> values, Map<Long, String> from, Map<Long, String> to,
                             Map<Long, FilterOperator> operators) {
    }

    /**
     * One {@code f.<fieldId>[.from|.to|.op]} parameter, sorted into the value, lower-bound, upper-bound
     * or operator map.
     */
    private static void collect(String rawKey, @Nullable List<String> rawValues, Collected collected) {
        if (!isFieldParameter(rawKey)) return;
        String key = rawKey.substring(2);
        int suffix = key.indexOf('.', 1);
        String part = suffix < 0 ? null : key.substring(suffix + 1);
        long fieldId = Long.parseLong(suffix < 0 ? key : key.substring(0, suffix));
        List<String> raw = rawValues == null ? List.of() : rawValues;
        if (part == null) {
            collected.values().computeIfAbsent(fieldId, k -> new ArrayList<>()).addAll(raw);
            return;
        }
        if (raw.size() != 1) throw badRequest("Une seule valeur attendue pour : " + rawKey);
        String value = raw.get(0).trim();
        if (value.isEmpty()) return;
        switch (part) {
            case "from" -> collected.from().put(fieldId, value);
            case "to" -> collected.to().put(fieldId, value);
            default -> collected.operators().put(fieldId, FilterOperator.fromWire(value)
                    .orElseThrow(() -> badRequest("Opérateur inconnu pour " + rawKey + " : " + value)));
        }
    }

    private static Map<Long, Criterion> criteria(Collected collected) {
        Map<Long, Criterion> filters = new LinkedHashMap<>();
        for (Long fieldId : union(collected.values().keySet(), collected.from().keySet(), collected.to().keySet(),
                collected.operators().keySet())) {
            List<String> v = collected.values().getOrDefault(fieldId, List.of()).stream()
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            boolean range = collected.from().containsKey(fieldId) || collected.to().containsKey(fieldId);
            FilterOperator operator = collected.operators().get(fieldId);
            if (range && !v.isEmpty()) {
                throw badRequest("Filtre à la fois en valeur et en plage : f." + fieldId);
            }
            boolean takesValues = operator == null || operator.takesValues();
            if (!takesValues && (range || !v.isEmpty())) {
                throw badRequest("L'opérateur " + operator.wireName() + " ne prend pas de valeur : f." + fieldId);
            }
            // A filter still waiting for its value narrows nothing: dropped, like a blank one.
            if (takesValues && !range && v.isEmpty()) continue;
            filters.put(fieldId, new Criterion(v, collected.from().get(fieldId), collected.to().get(fieldId), operator));
        }
        return filters;
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

    @SafeVarargs
    private static List<Long> union(java.util.Set<Long>... sets) {
        java.util.LinkedHashSet<Long> out = new java.util.LinkedHashSet<>();
        for (java.util.Set<Long> set : sets) out.addAll(set);
        return new ArrayList<>(out);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
