package fr.siamois.ui.utils;

import fr.siamois.dto.view.FilterState;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FilterStateTooltipHelperTest {

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static FilterState state(FilterState.FilterType type, Object value) {
        FilterState s = new FilterState();
        s.setType(type);
        s.setValue(value);
        return s;
    }

    private static Map<String, Object> label(String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("label", text);
        return m;
    }

    // ------------------------------------------------------------------
    // buildTooltip — null / empty guard
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_nullMap_returnsNoActiveFilter() {
        assertEquals("Aucun filtre actif", FilterStateTooltipHelper.buildTooltip(null));
    }

    @Test
    void buildTooltip_emptyMap_returnsNoActiveFilter() {
        assertEquals("Aucun filtre actif", FilterStateTooltipHelper.buildTooltip(new LinkedHashMap<>()));
    }

    // ------------------------------------------------------------------
    // buildTooltip — null / null-value entries are skipped
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_nullFilterStateEntry_isSkipped() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("a", null);

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertFalse(result.contains("•"), "null entry should produce no bullet");
    }

    @Test
    void buildTooltip_filterStateWithNullValue_isSkipped() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("a", state(FilterState.FilterType.TEXT, null));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertFalse(result.contains("•"));
    }

    // ------------------------------------------------------------------
    // buildTooltip — TEXT
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_textFilter_formatsCorrectly() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("name", state(FilterState.FilterType.TEXT, "fouille"));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertTrue(result.contains("Texte : fouille"));
        assertTrue(result.contains("•"));
    }

    // ------------------------------------------------------------------
    // buildTooltip — BOOLEAN
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_booleanTrue_formatsCorrectly() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("active", state(FilterState.FilterType.BOOLEAN, true));

        assertTrue(FilterStateTooltipHelper.buildTooltip(map).contains("Booléen : true"));
    }

    @Test
    void buildTooltip_booleanFalse_formatsCorrectly() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("active", state(FilterState.FilterType.BOOLEAN, false));

        assertTrue(FilterStateTooltipHelper.buildTooltip(map).contains("Booléen : false"));
    }

    // ------------------------------------------------------------------
    // buildTooltip — entity types (CONCEPT / PERSON / ACTION_UNIT / SPATIAL_UNIT)
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_conceptWithLabels_formatsWithPrefix() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("type", state(FilterState.FilterType.CONCEPT, List.of(label("Fouille"), label("Prospection"))));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertTrue(result.contains("Concept : Fouille, Prospection"));
    }

    @Test
    void buildTooltip_personWithLabels_formatsWithPrefix() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("author", state(FilterState.FilterType.PERSON, List.of(label("Alice"), label("Bob"))));

        assertTrue(FilterStateTooltipHelper.buildTooltip(map).contains("Personne : Alice, Bob"));
    }

    @Test
    void buildTooltip_actionUnitWithLabels_formatsWithPrefix() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("action", state(FilterState.FilterType.ACTION_UNIT, List.of(label("Fouille 2024"))));

        assertTrue(FilterStateTooltipHelper.buildTooltip(map).contains("Action : Fouille 2024"));
    }

    @Test
    void buildTooltip_spatialUnitWithLabels_formatsWithPrefix() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("place", state(FilterState.FilterType.SPATIAL_UNIT, List.of(label("Site Nord"))));

        assertTrue(FilterStateTooltipHelper.buildTooltip(map).contains("Unité spatiale : Site Nord"));
    }

    @Test
    void buildTooltip_entityWithEmptyList_isSkipped() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("type", state(FilterState.FilterType.CONCEPT, List.of()));

        assertFalse(FilterStateTooltipHelper.buildTooltip(map).contains("•"));
    }

    @Test
    void buildTooltip_entityWithNonListValue_isSkipped() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("type", state(FilterState.FilterType.CONCEPT, "not-a-list"));

        assertFalse(FilterStateTooltipHelper.buildTooltip(map).contains("•"));
    }

    @Test
    void buildTooltip_entityListWithNoLabelKey_isSkipped() {
        Map<String, Object> noLabel = new HashMap<>();
        noLabel.put("id", 42);
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("type", state(FilterState.FilterType.CONCEPT, List.of(noLabel)));

        assertFalse(FilterStateTooltipHelper.buildTooltip(map).contains("•"));
    }

    @Test
    void buildTooltip_entityListWithNullEntries_nullsAreFilteredOut() {
        List<Object> listWithNull = new ArrayList<>();
        listWithNull.add(null);
        listWithNull.add(label("Valid"));
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("type", state(FilterState.FilterType.CONCEPT, listWithNull));

        assertTrue(FilterStateTooltipHelper.buildTooltip(map).contains("Concept : Valid"));
    }

    // ------------------------------------------------------------------
    // buildTooltip — joinWithLimit (via CONCEPT)
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_entityListUpToFiveLabels_joinsWithComma() {
        List<Object> five = List.of(label("A"), label("B"), label("C"), label("D"), label("E"));
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("type", state(FilterState.FilterType.CONCEPT, five));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertTrue(result.contains("A, B, C, D, E"));
        assertFalse(result.contains("autre"));
    }

    @Test
    void buildTooltip_entityListMoreThanFiveLabels_truncatesWithOtherCount() {
        List<Object> seven = List.of(
                label("A"), label("B"), label("C"), label("D"), label("E"), label("F"), label("G"));
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("type", state(FilterState.FilterType.CONCEPT, seven));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertTrue(result.contains("A, B, C, D, E"));
        assertTrue(result.contains("et 2 autre(s)"));
        assertFalse(result.contains("F"));
    }

    // ------------------------------------------------------------------
    // buildTooltip — DATE_RANGE
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_dateRangeBothDates_formatsRange() {
        Date from = new Date(0);
        Date to = new Date(86_400_000L);
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("date", state(FilterState.FilterType.DATE_RANGE, Arrays.asList(from, to)));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertTrue(result.contains("Date :"));
        assertTrue(result.contains("→"));
    }

    @Test
    void buildTooltip_dateRangeFromOnly_formatsAfter() {
        Date from = new Date(0);
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("date", state(FilterState.FilterType.DATE_RANGE, Collections.singletonList(from)));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertTrue(result.contains("Date après :"));
    }

    @Test
    void buildTooltip_dateRangeToOnly_formatsBefore() {
        Date to = new Date(86_400_000L);
        List<Date> list = new ArrayList<>();
        list.add(null);
        list.add(to);
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("date", state(FilterState.FilterType.DATE_RANGE, list));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        assertTrue(result.contains("Date avant :"));
    }

    @Test
    void buildTooltip_dateRangeEmptyList_isSkipped() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("date", state(FilterState.FilterType.DATE_RANGE, List.of()));

        assertFalse(FilterStateTooltipHelper.buildTooltip(map).contains("•"));
    }

    @Test
    void buildTooltip_dateRangeNonListValue_isSkipped() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("date", state(FilterState.FilterType.DATE_RANGE, "not-a-list"));

        assertFalse(FilterStateTooltipHelper.buildTooltip(map).contains("•"));
    }

    @Test
    void buildTooltip_dateRangeBothDatesNull_isSkipped() {
        List<Object> list = new ArrayList<>();
        list.add(null);
        list.add(null);
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("date", state(FilterState.FilterType.DATE_RANGE, list));

        assertFalse(FilterStateTooltipHelper.buildTooltip(map).contains("•"));
    }

    // ------------------------------------------------------------------
    // buildTooltip — NUMBER falls through to default → skipped
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_numberType_isSkippedViaDefault() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("n", state(FilterState.FilterType.NUMBER, 42));

        assertFalse(FilterStateTooltipHelper.buildTooltip(map).contains("•"));
    }

    // ------------------------------------------------------------------
    // buildTooltip — DATE (legacy, not DATE_RANGE) falls through → skipped
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_dateType_isSkippedViaDefault() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("d", state(FilterState.FilterType.DATE, new Date(0)));

        assertFalse(FilterStateTooltipHelper.buildTooltip(map).contains("•"));
    }

    // ------------------------------------------------------------------
    // buildTooltip — multiple filters produce multiple bullets
    // ------------------------------------------------------------------

    @Test
    void buildTooltip_multipleFilters_producesOneBulletPerActiveFilter() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("name", state(FilterState.FilterType.TEXT, "fouille"));
        map.put("author", state(FilterState.FilterType.PERSON, List.of(label("Alice"))));

        String result = FilterStateTooltipHelper.buildTooltip(map);
        long bullets = result.chars().filter(c -> c == '•').count();
        assertEquals(2, bullets);
    }

    // ------------------------------------------------------------------
    // buildOrderedTooltip — null / empty guard
    // ------------------------------------------------------------------

    @Test
    void buildOrderedTooltip_nullMap_returnsNoActiveFilter() {
        assertEquals("Aucun filtre actif", FilterStateTooltipHelper.buildOrderedTooltip(null));
    }

    @Test
    void buildOrderedTooltip_emptyMap_returnsNoActiveFilter() {
        assertEquals("Aucun filtre actif", FilterStateTooltipHelper.buildOrderedTooltip(new LinkedHashMap<>()));
    }

    // ------------------------------------------------------------------
    // buildOrderedTooltip — prefix and content
    // ------------------------------------------------------------------

    @Test
    void buildOrderedTooltip_withFilters_startsWithFiltresActifs() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("name", state(FilterState.FilterType.TEXT, "test"));

        assertTrue(FilterStateTooltipHelper.buildOrderedTooltip(map).startsWith("Filtres actifs :"));
    }

    @Test
    void buildOrderedTooltip_nullValueEntry_isSkipped() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("a", state(FilterState.FilterType.TEXT, null));

        String result = FilterStateTooltipHelper.buildOrderedTooltip(map);
        assertFalse(result.contains("•"));
    }

    @Test
    void buildOrderedTooltip_formatStateReturnsNull_isSkipped() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        // NUMBER → default → null
        map.put("n", state(FilterState.FilterType.NUMBER, 5));

        String result = FilterStateTooltipHelper.buildOrderedTooltip(map);
        assertFalse(result.contains("•"));
    }

    @Test
    void buildOrderedTooltip_multipleTypes_sortsAndProducesBullets() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("b", state(FilterState.FilterType.BOOLEAN, true));
        map.put("t", state(FilterState.FilterType.TEXT, "val"));
        map.put("p", state(FilterState.FilterType.PERSON, List.of(label("Alice"))));

        String result = FilterStateTooltipHelper.buildOrderedTooltip(map);
        long bullets = result.chars().filter(c -> c == '•').count();
        assertEquals(3, bullets);
    }

    @Test
    void buildOrderedTooltip_nullStateEntries_areFilteredBeforeSorting() {
        Map<String, FilterState> map = new LinkedHashMap<>();
        map.put("x", null);
        map.put("t", state(FilterState.FilterType.TEXT, "ok"));

        String result = FilterStateTooltipHelper.buildOrderedTooltip(map);
        assertTrue(result.contains("Texte : ok"));
    }
}
