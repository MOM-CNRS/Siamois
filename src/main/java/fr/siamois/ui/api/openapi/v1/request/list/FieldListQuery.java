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
 * The field-keyed part of a list request: {@code sort=<fieldId>:asc|desc} and
 * {@code f.<fieldId>[.from|.to]=…}, where {@code <fieldId>} is a custom field id (negative for a
 * system field). Named keys ({@code sort=fullIdentifier:asc}, {@code f.type=…}) stay with each
 * list's own parser, which skips the numeric ones ({@link #isFieldKey}).
 *
 * <p>Only the syntax is checked here; what a value means (text, number, date, ids) depends on the
 * field, which {@code FieldQueryService} resolves. {@code lang} is the caller's language, which a
 * sort by concept label reads.</p>
 */
public record FieldListQuery(Map<Long, Criterion> filters, @Nullable Long sortFieldId, boolean ascending, String lang) {

    public static final FieldListQuery NONE = new FieldListQuery(Map.of(), null, true, "fr");

    private static final Pattern FIELD_KEY = Pattern.compile("-?\\d+");

    /** The raw values sent for one field: {@code values} for a bare key, the bounds for a range. */
    public record Criterion(List<String> values, @Nullable String from, @Nullable String to) {
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
        if (queryParams != null) {
            for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
                String rawKey = entry.getKey();
                if (!rawKey.startsWith("f.")) continue;
                String key = rawKey.substring(2);
                String bound = null;
                if (key.endsWith(".from")) {
                    bound = "from";
                    key = key.substring(0, key.length() - ".from".length());
                } else if (key.endsWith(".to")) {
                    bound = "to";
                    key = key.substring(0, key.length() - ".to".length());
                }
                if (!isFieldKey(key)) continue;
                long fieldId = Long.parseLong(key);
                List<String> raw = entry.getValue() == null ? List.of() : entry.getValue();
                if (bound == null) {
                    values.computeIfAbsent(fieldId, k -> new ArrayList<>()).addAll(raw);
                } else {
                    if (raw.size() != 1) throw badRequest("Une seule valeur attendue pour : " + rawKey);
                    String value = raw.get(0).trim();
                    if (value.isEmpty()) continue;
                    ("from".equals(bound) ? from : to).put(fieldId, value);
                }
            }
        }

        Map<Long, Criterion> filters = new LinkedHashMap<>();
        for (Long fieldId : union(values.keySet(), from.keySet(), to.keySet())) {
            List<String> v = values.getOrDefault(fieldId, List.of()).stream()
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            boolean range = from.containsKey(fieldId) || to.containsKey(fieldId);
            if (range && !v.isEmpty()) {
                throw badRequest("Filtre à la fois en valeur et en plage : f." + fieldId);
            }
            if (!range && v.isEmpty()) continue;
            filters.put(fieldId, new Criterion(v, from.get(fieldId), to.get(fieldId)));
        }

        Long sortFieldId = null;
        boolean ascending = true;
        if (isFieldSort(sort)) {
            sortFieldId = Long.parseLong(sortProperty(sort));
            int colon = sort.lastIndexOf(':');
            String direction = colon < 0 ? "asc" : sort.substring(colon + 1).trim().toLowerCase();
            if (!direction.equals("asc") && !direction.equals("desc")) {
                throw badRequest("Direction de tri invalide : " + sort);
            }
            ascending = direction.equals("asc");
        }
        return new FieldListQuery(Map.copyOf(filters), sortFieldId, ascending, lang);
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
