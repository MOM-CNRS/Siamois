package fr.siamois.ui.api.openapi.v1.request.project;

import fr.siamois.ui.api.openapi.v1.request.list.FieldListQuery;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-column filters on {@code GET /api/v1/projects}, parsed from {@code f.<key>} query params.
 *
 * <p>Contract: {@code f.status=12&f.status=44} (repeatable → OR within the key, AND across keys),
 * {@code f.name=foss} (bare key → text contains), {@code f.zmin.from=10&f.zmin.to=40} (numeric
 * range), {@code f.beginDate.from=2024-01-01} (date range, ISO-8601). Scoped to the
 * default-visible columns (plan §3 phase 3) — {@link #FILTERABLE_FIELDS} is the whitelist; an
 * unknown key or an unparseable value is a <strong>400</strong>, never a silent fallback (that
 * silent-fallback pattern is exactly what hid the {@code ?sort=} binding bug this migration
 * already fixed once).</p>
 */
public record ProjectListFilter(
        Map<String, String> containsFilters,
        Map<String, List<Long>> conceptOneInFilters,
        Map<String, List<Long>> conceptManyInFilters,
        Map<String, List<Long>> spatialOneInFilters,
        Map<String, NumericRange> numericRangeFilters,
        // Not a query-string filter: set server-side (withIdIn) to restrict the list to the projects
        // the caller may create something in (GET /projects?canCreate=…). Null = no restriction.
        Set<Long> idIn
) {
    public static final ProjectListFilter EMPTY =
            new ProjectListFilter(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    public ProjectListFilter(Map<String, String> containsFilters,
                             Map<String, List<Long>> conceptOneInFilters,
                             Map<String, List<Long>> conceptManyInFilters,
                             Map<String, List<Long>> spatialOneInFilters,
                             Map<String, NumericRange> numericRangeFilters) {
        this(containsFilters, conceptOneInFilters, conceptManyInFilters, spatialOneInFilters, numericRangeFilters, null);
    }

    /** This filter, restricted to the given project ids (an empty set matches nothing). */
    public ProjectListFilter withIdIn(Set<Long> ids) {
        return new ProjectListFilter(containsFilters, conceptOneInFilters, conceptManyInFilters,
                spatialOneInFilters, numericRangeFilters, Set.copyOf(ids));
    }

    public record NumericRange(Double from, Double to) {}

    /** This filter, restricted to projects whose spatial context contains {@code placeId}. */
    public ProjectListFilter withSpatialContext(long placeId) {
        Map<String, List<Long>> manyIn = new LinkedHashMap<>(conceptManyInFilters);
        manyIn.put("spatialContext", List.of(placeId));
        return new ProjectListFilter(containsFilters, conceptOneInFilters, Map.copyOf(manyIn),
                spatialOneInFilters, numericRangeFilters, idIn);
    }

    public boolean isEmpty() {
        return containsFilters.isEmpty() && conceptOneInFilters.isEmpty() && conceptManyInFilters.isEmpty()
                && spatialOneInFilters.isEmpty() && numericRangeFilters.isEmpty() && idIn == null;
    }

    // DATE_RANGE isn't wired into FILTERABLE_FIELDS below: ActionUnitTableColumnDefaults doesn't
    // include beginDate/endDate at all (ActionUnitTableDefinitionFactory never showed them in the
    // JSF list either — see the phase-2 columns.tsx note), so per the "default-visible columns
    // only" filter scope there is currently no date-range target. The machinery (DateRange,
    // parseDate) is intentionally kept minimal rather than fully wired, so a future visible date
    // column is a whitelist entry away, not a redesign.
    private enum Kind { TEXT_CONTAINS, CONCEPT_ONE_IN, CONCEPT_MANY_IN, SPATIAL_ONE_IN, NUMERIC_RANGE }

    /**
     * Whitelist of filterable column ids — deliberately just the 9 default-visible columns
     * (plan §3 phase 3: JSF only ever actually filtered on name/fullIdentifier/global search, so
     * building 25 is a feature expansion, not parity; this widens it to every default-visible
     * column rather than the strict-parity 2). Not the same keyspace as
     * {@code ActionUnitTableColumnDefaults}' columnId — deliberately includes {@code name} and
     * {@code fullIdentifier}, the two pinned columns that aren't part of that catalog.
     */
    private static final Map<String, Kind> FILTERABLE_FIELDS = Map.ofEntries(
            Map.entry("name", Kind.TEXT_CONTAINS),
            Map.entry("fullIdentifier", Kind.TEXT_CONTAINS),
            Map.entry("oaCode", Kind.TEXT_CONTAINS),
            Map.entry("scientificManager", Kind.TEXT_CONTAINS),
            Map.entry("status", Kind.CONCEPT_ONE_IN),
            Map.entry("periods", Kind.CONCEPT_MANY_IN),
            Map.entry("subjects", Kind.CONCEPT_MANY_IN),
            Map.entry("mainLocation", Kind.SPATIAL_ONE_IN),
            // ActionUnit.spatialContext is a @ManyToMany of places: same EXISTS-on-collection filter as
            // periods/subjects (ActionUnitFilterSpec#conceptManyIn isn't concept-specific at runtime).
            // Also what GET /places/{id}/projects forces (withSpatialContext).
            Map.entry("spatialContext", Kind.CONCEPT_MANY_IN),
            Map.entry("openingRate", Kind.NUMERIC_RANGE)
    );

    public static ProjectListFilter parse(MultiValueMap<String, String> queryParams) {
        Map<String, String> contains = new LinkedHashMap<>();
        Map<String, List<Long>> conceptOne = new LinkedHashMap<>();
        Map<String, List<Long>> conceptMany = new LinkedHashMap<>();
        Map<String, List<Long>> spatialOne = new LinkedHashMap<>();
        Map<String, Double> numericFrom = new LinkedHashMap<>();
        Map<String, Double> numericTo = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            String rawKey = entry.getKey();
            if (!rawKey.startsWith("f.")) continue;
            List<String> values = entry.getValue();

            String afterPrefix = rawKey.substring("f.".length());
            String baseKey = afterPrefix;
            String rangeBound = null;
            if (afterPrefix.endsWith(".from")) {
                baseKey = afterPrefix.substring(0, afterPrefix.length() - ".from".length());
                rangeBound = "from";
            } else if (afterPrefix.endsWith(".to")) {
                baseKey = afterPrefix.substring(0, afterPrefix.length() - ".to".length());
                rangeBound = "to";
            }

            // f.<fieldId>: a field-keyed filter, FieldListQuery's.
            if (FieldListQuery.isFieldKey(baseKey)) continue;

            Kind kind = FILTERABLE_FIELDS.get(baseKey);
            if (kind == null) {
                throw badRequest("Filtre inconnu : " + rawKey);
            }

            switch (kind) {
                case TEXT_CONTAINS -> {
                    requireNoRangeSuffix(rawKey, rangeBound);
                    String value = requireSingleValue(rawKey, values);
                    if (!value.isBlank()) contains.put(baseKey, value);
                }
                case CONCEPT_ONE_IN -> {
                    requireNoRangeSuffix(rawKey, rangeBound);
                    conceptOne.computeIfAbsent(baseKey, k -> new ArrayList<>()).addAll(parseLongs(rawKey, values));
                }
                case CONCEPT_MANY_IN -> {
                    requireNoRangeSuffix(rawKey, rangeBound);
                    conceptMany.computeIfAbsent(baseKey, k -> new ArrayList<>()).addAll(parseLongs(rawKey, values));
                }
                case SPATIAL_ONE_IN -> {
                    requireNoRangeSuffix(rawKey, rangeBound);
                    spatialOne.computeIfAbsent(baseKey, k -> new ArrayList<>()).addAll(parseLongs(rawKey, values));
                }
                case NUMERIC_RANGE -> {
                    requireRangeSuffix(rawKey, rangeBound);
                    Double value = parseDouble(rawKey, requireSingleValue(rawKey, values));
                    (("from".equals(rangeBound)) ? numericFrom : numericTo).put(baseKey, value);
                }
            }
        }

        Map<String, NumericRange> numericRanges = new LinkedHashMap<>();
        for (String key : union(numericFrom.keySet(), numericTo.keySet())) {
            numericRanges.put(key, new NumericRange(numericFrom.get(key), numericTo.get(key)));
        }

        return new ProjectListFilter(
                Map.copyOf(contains), Map.copyOf(conceptOne), Map.copyOf(conceptMany),
                Map.copyOf(spatialOne), Map.copyOf(numericRanges));
    }

    private static java.util.Set<String> union(java.util.Set<String> a, java.util.Set<String> b) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>(a);
        out.addAll(b);
        return out;
    }

    private static void requireNoRangeSuffix(String rawKey, String rangeBound) {
        if (rangeBound != null) throw badRequest("Filtre non applicable en plage : " + rawKey);
    }

    private static void requireRangeSuffix(String rawKey, String rangeBound) {
        if (rangeBound == null) {
            throw badRequest("Filtre attendu en plage (.from/.to) : " + rawKey);
        }
    }

    private static String requireSingleValue(String rawKey, List<String> values) {
        if (values == null || values.size() != 1) {
            throw badRequest("Une seule valeur attendue pour : " + rawKey);
        }
        return values.get(0);
    }

    private static List<Long> parseLongs(String rawKey, List<String> values) {
        List<Long> out = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                out.add(Long.parseLong(value.trim()));
            } catch (NumberFormatException e) {
                throw badRequest("Identifiant invalide pour " + rawKey + " : " + value);
            }
        }
        return out;
    }

    private static Double parseDouble(String rawKey, String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw badRequest("Valeur numérique invalide pour " + rawKey + " : " + value);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
