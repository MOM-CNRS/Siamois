package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import fr.siamois.dto.FilterDTO;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.ui.api.openapi.v1.request.list.FieldListQuery;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-column filters on {@code GET /api/v1/projects/{id}/recording-units}, parsed from
 * {@code f.<key>} query params — the same wire contract {@link fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter}
 * uses for the project list.
 *
 * <p>Deliberately its own record rather than sharing a base with {@code ProjectListFilter}: the two
 * have neither the same bucket set nor the same target shape. {@code ProjectListFilter}'s five
 * buckets exist because {@code ActionUnitFilterSpec} applies a different JPA strategy per bucket;
 * every filter here instead becomes one {@link FilterDTO} entry that
 * {@link fr.siamois.domain.services.recordingunit.RecordingUnitSortFilterService#userFilterSpecs}
 * already knows how to turn into a {@link org.springframework.data.jpa.domain.Specification} — an
 * id-list filter needs no per-column JPA knowledge here at all. Revisit extracting a shared base
 * once a third list filter exists; two is not enough to tell what the right abstraction is.</p>
 *
 * <p>Contract: {@code f.type=12&f.type=44} (repeatable → OR within the key, AND across keys),
 * {@code f.fullIdentifier=UE42} (bare key → text contains), {@code f.tpq.from=10&f.tpq.to=40}
 * (integer range), {@code f.openingDate.from=2024-01-01T00:00:00Z} (date range, ISO-8601 offset
 * date-time). {@link #FILTERABLE_FIELDS} is the whitelist, taken from
 * {@code BaseRecordingUnitLazyDataModel.prepareFilterDTO} — every key is a
 * {@link RecordingUnitSpec} constant, so the wire key and the {@link FilterDTO} key are the same
 * string, no translation table. An unknown key or an unparseable value is a <strong>400</strong>,
 * never a silent fallback. {@code f.actionUnit} is deliberately NOT in the whitelist: the path
 * already scopes the list to one project, so accepting it would be a second, conflicting scope.</p>
 */
public record RecordingUnitListFilter(
        Map<String, String> containsFilters,
        Map<String, List<Long>> idListFilters,
        Map<String, DateRange> dateRangeFilters,
        Map<String, IntRange> intRangeFilters
) {
    public static final RecordingUnitListFilter EMPTY =
            new RecordingUnitListFilter(Map.of(), Map.of(), Map.of(), Map.of());

    public record DateRange(OffsetDateTime from, OffsetDateTime to) {}

    public record IntRange(Integer from, Integer to) {}

    public boolean isEmpty() {
        return containsFilters.isEmpty() && idListFilters.isEmpty()
                && dateRangeFilters.isEmpty() && intRangeFilters.isEmpty();
    }

    private enum Kind { TEXT_CONTAINS, ID_LIST, DATE_RANGE, INT_RANGE }

    private static final Map<String, Kind> FILTERABLE_FIELDS = Map.ofEntries(
            Map.entry(RecordingUnitSpec.FULL_IDENTIFIER, Kind.TEXT_CONTAINS),
            Map.entry(RecordingUnitSpec.MATRIX_FILTER, Kind.TEXT_CONTAINS),
            Map.entry(RecordingUnitSpec.TYPE_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.NATURE_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.AGENT_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.INTERPRETATION_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.AUTHOR_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.CONTRIBUTORS_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.SPATIAL_UNIT_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.PARENTS_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.CHILDREN_FILTER, Kind.ID_LIST),
            Map.entry(RecordingUnitSpec.OPENING_DATE_FILTER, Kind.DATE_RANGE),
            Map.entry(RecordingUnitSpec.CLOSING_DATE_FILTER, Kind.DATE_RANGE),
            Map.entry(RecordingUnitSpec.TPQ_FILTER, Kind.INT_RANGE),
            Map.entry(RecordingUnitSpec.TAQ_FILTER, Kind.INT_RANGE)
    );

    public static RecordingUnitListFilter parse(MultiValueMap<String, String> queryParams) {
        Map<String, String> contains = new LinkedHashMap<>();
        Map<String, List<Long>> idLists = new LinkedHashMap<>();
        Map<String, OffsetDateTime> dateFrom = new LinkedHashMap<>();
        Map<String, OffsetDateTime> dateTo = new LinkedHashMap<>();
        Map<String, Integer> intFrom = new LinkedHashMap<>();
        Map<String, Integer> intTo = new LinkedHashMap<>();

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
                case ID_LIST -> {
                    requireNoRangeSuffix(rawKey, rangeBound);
                    idLists.computeIfAbsent(baseKey, k -> new ArrayList<>()).addAll(parseLongs(rawKey, values));
                }
                case DATE_RANGE -> {
                    requireRangeSuffix(rawKey, rangeBound);
                    OffsetDateTime value = parseDate(rawKey, requireSingleValue(rawKey, values));
                    (("from".equals(rangeBound)) ? dateFrom : dateTo).put(baseKey, value);
                }
                case INT_RANGE -> {
                    requireRangeSuffix(rawKey, rangeBound);
                    Integer value = parseInt(rawKey, requireSingleValue(rawKey, values));
                    (("from".equals(rangeBound)) ? intFrom : intTo).put(baseKey, value);
                }
            }
        }

        Map<String, DateRange> dateRanges = new LinkedHashMap<>();
        for (String key : union(dateFrom.keySet(), dateTo.keySet())) {
            dateRanges.put(key, new DateRange(dateFrom.get(key), dateTo.get(key)));
        }
        Map<String, IntRange> intRanges = new LinkedHashMap<>();
        for (String key : union(intFrom.keySet(), intTo.keySet())) {
            intRanges.put(key, new IntRange(intFrom.get(key), intTo.get(key)));
        }

        return new RecordingUnitListFilter(
                Map.copyOf(contains), Map.copyOf(idLists), Map.copyOf(dateRanges), Map.copyOf(intRanges));
    }

    /**
     * Turns this filter (plus the free-text {@code search} param, which lands on the same
     * {@code fullIdentifier} column) into the {@link FilterDTO}
     * {@link fr.siamois.domain.services.recordingunit.RecordingUnitService#findByActionUnitId(Long, int, int, org.springframework.data.domain.Sort, FilterDTO)}
     * consumes. An explicit {@code f.fullIdentifier} filter wins over {@code search} for the same
     * column — both are added in that order and {@link FilterDTO#add} overwrites.
     */
    public FilterDTO toFilterDTO(String search) {
        FilterDTO dto = new FilterDTO();
        if (search != null && !search.isBlank()) {
            dto.add(RecordingUnitSpec.FULL_IDENTIFIER, search, FilterDTO.FilterType.CONTAINS);
        }
        for (Map.Entry<String, String> e : containsFilters.entrySet()) {
            dto.add(e.getKey(), e.getValue(), FilterDTO.FilterType.CONTAINS);
        }
        for (Map.Entry<String, List<Long>> e : idListFilters.entrySet()) {
            dto.add(e.getKey(), e.getValue(), FilterDTO.FilterType.CONTAINS);
        }
        for (Map.Entry<String, DateRange> e : dateRangeFilters.entrySet()) {
            List<OffsetDateTime> range = new ArrayList<>();
            range.add(e.getValue().from());
            range.add(e.getValue().to());
            dto.add(e.getKey(), range, FilterDTO.FilterType.CONTAINS);
        }
        for (Map.Entry<String, IntRange> e : intRangeFilters.entrySet()) {
            List<Integer> range = new ArrayList<>();
            range.add(e.getValue().from());
            range.add(e.getValue().to());
            dto.add(e.getKey(), range, FilterDTO.FilterType.CONTAINS);
        }
        return dto;
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new java.util.LinkedHashSet<>(a);
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

    private static OffsetDateTime parseDate(String rawKey, String value) {
        try {
            return OffsetDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw badRequest("Date invalide (ISO-8601 attendu) pour " + rawKey + " : " + value);
        }
    }

    private static Integer parseInt(String rawKey, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw badRequest("Valeur entière invalide pour " + rawKey + " : " + value);
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
