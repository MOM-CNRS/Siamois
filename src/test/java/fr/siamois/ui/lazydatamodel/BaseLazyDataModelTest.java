package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.MatchMode;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"rawtypes", "unchecked"})
@ExtendWith(MockitoExtension.class)
class BaseLazyDataModelTest {

    Map<String, FilterMeta> filterBy;
    Map<String, FilterMeta> filterBy2;
    Map<String, SortMeta> sortBy;
    Map<String, SortMeta> sortBy2;

    @Test
    void isSortCriteriaSame_bothNull() {

        sortBy2 = null;
        sortBy = null;

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(sortBy, sortBy2);
        assertTrue(res);
    }

    @Test
    void isSortCriteriaSame_oneNull() {

        sortBy2 = null;
        sortBy = new HashMap<>();

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isSortCriteriaSame(sortBy, sortBy2);
        assertFalse(res);
    }

    @Test
    void isSortCriteriaSame_differentSize() {

        sortBy2 = new HashMap<>();
        sortBy = new HashMap<>();
        sortBy2.put("sort1", SortMeta.builder().field("sort1").build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isSortCriteriaSame(sortBy, sortBy2);
        assertFalse(res);
    }

    @Test
    void isSortCriteriaSame_fieldNotFound() {

        sortBy2 = new HashMap<>();
        sortBy = new HashMap<>();
        sortBy2.put("sort1", SortMeta.builder().field("sort1").order(SortOrder.ASCENDING).build());
        sortBy.put("sort2", SortMeta.builder().field("sort2").order(SortOrder.ASCENDING).build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isSortCriteriaSame(sortBy, sortBy2);
        assertFalse(res);
    }

    @Test
    void isSortCriteriaSame_fieldNotEqual() {

        sortBy2 = new HashMap<>();
        sortBy = new HashMap<>();
        sortBy2.put("sort1", SortMeta.builder().field("sort1").order(SortOrder.ASCENDING).build());
        sortBy.put("sort1", SortMeta.builder().field("sort1").order(SortOrder.DESCENDING).build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isSortCriteriaSame(sortBy, sortBy2);
        assertFalse(res);
    }

    @Test
    void isSortCriteriaSame_fieldEqual() {

        sortBy2 = new HashMap<>();
        sortBy = new HashMap<>();
        sortBy2.put("sort1", SortMeta.builder().field("sort1").order(SortOrder.ASCENDING).build());
        sortBy.put("sort1", SortMeta.builder().field("sort1").order(SortOrder.ASCENDING).build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isSortCriteriaSame(sortBy, sortBy2);
        assertTrue(res);
    }

    @Test
    void isFilterCriteriaSame_bothNull() {

        filterBy2 = null;
        filterBy = null;

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertTrue(res);
    }

    @Test
    void isFilterCriteriaSame_oneNull() {

        filterBy2 = null;
        filterBy = new HashMap<>();

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertFalse(res);
    }

    @Test
    void isFilterCriteriaSame_differentSize() {

        filterBy2 = new HashMap<>();
        filterBy = new HashMap<>();
        filterBy2.put("f1", FilterMeta.builder().field("f1").build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertFalse(res);
    }

    @Test
    void isFilterCriteriaSame_fieldNotFound() {

        filterBy2 = new HashMap<>();
        filterBy = new HashMap<>();
        filterBy2.put("f1", FilterMeta.builder().field("f1").filterValue("test").build());
        filterBy.put("f2", FilterMeta.builder().field("f2").filterValue("test").build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertFalse(res);
    }

    @Test
    void isFilterCriteriaSame_fieldNotEqual_String() {

        filterBy2 = new HashMap<>();
        filterBy = new HashMap<>();
        filterBy2.put("f1", FilterMeta.builder().field("f1").filterValue("test").build());
        filterBy.put("f1", FilterMeta.builder().field("f1").filterValue("test2").build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertFalse(res);
    }

    @Test
    void isFilterCriteriaSame_fieldNotEqual_Object() {

        filterBy2 = new HashMap<>();
        filterBy = new HashMap<>();
        RecordingUnit r = new RecordingUnit();
        RecordingUnit r2 = new RecordingUnit();
        r.setFullIdentifier("f1");
        r2.setFullIdentifier("f2");
        filterBy2.put("f1", FilterMeta.builder().field("f1").filterValue(r).build());
        filterBy.put("f1", FilterMeta.builder().field("f1").filterValue(r2).build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertFalse(res);
    }

    @Test
    void isFilterCriteriaSame_fieldNotEqual_Collection() {

        filterBy2 = new HashMap<>();
        filterBy = new HashMap<>();
        RecordingUnit r = new RecordingUnit();
        RecordingUnit r2 = new RecordingUnit();
        RecordingUnit r3 = new RecordingUnit();
        RecordingUnit r4 = new RecordingUnit();
        r.setFullIdentifier("f1");
        r2.setFullIdentifier("f2");
        r3.setFullIdentifier("f1");
        r4.setFullIdentifier("f3");
        List<RecordingUnit> l1 = new ArrayList<>();
        l1.add(r);
        l1.add(r2);
        List<RecordingUnit> l2 = new ArrayList<>();
        l2.add(r3);
        l2.add(r4);
        filterBy2.put("f1", FilterMeta.builder().field("f1").filterValue(l1).build());
        filterBy.put("f1", FilterMeta.builder().field("f1").filterValue(l2).build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertFalse(res);
    }

    @Test
    void isFilterCriteriaSame_fieldEqual() {

        filterBy2 = new HashMap<>();
        filterBy = new HashMap<>();
        filterBy2.put("f1", FilterMeta.builder().field("f1").filterValue("test").build());
        filterBy.put("f1", FilterMeta.builder().field("f1").filterValue("test").build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertTrue(res);
    }

    @Test
    void isFilterCriteriaSame_fieldEqual_Object() {

        filterBy2 = new HashMap<>();
        filterBy = new HashMap<>();
        RecordingUnit r = new RecordingUnit();
        RecordingUnit r2 = new RecordingUnit();
        r.setFullIdentifier("f1");
        r2.setFullIdentifier("f1");
        filterBy2.put("f1", FilterMeta.builder().field("f1").filterValue(r).build());
        filterBy.put("f1", FilterMeta.builder().field("f1").filterValue(r2).build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertTrue(res);
    }

    @Test
    void isFilterCriteriaSame_fieldEqual_Collection() {

        filterBy2 = new HashMap<>();
        filterBy = new HashMap<>();
        RecordingUnit r = new RecordingUnit();
        RecordingUnit r2 = new RecordingUnit();
        RecordingUnit r3 = new RecordingUnit();
        RecordingUnit r4 = new RecordingUnit();
        r.setFullIdentifier("f1");
        r2.setFullIdentifier("f2");
        r3.setFullIdentifier("f2");
        r4.setFullIdentifier("f1");
        List<RecordingUnit> l1 = new ArrayList<>();
        l1.add(r);
        l1.add(r2);
        List<RecordingUnit> l2 = new ArrayList<>();
        l2.add(r3);
        l2.add(r4);
        filterBy2.put("f1", FilterMeta.builder().field("f1").filterValue(l1).build());
        filterBy.put("f1", FilterMeta.builder().field("f1").filterValue(l2).build());

        BaseLazyDataModel lazyModel = Mockito.mock(BaseLazyDataModel.class, Answers.CALLS_REAL_METHODS);

        // act
        boolean res = lazyModel.isFilterCriteriaSame(filterBy, filterBy2);
        assertTrue(res);
    }

    // ------------------------------------------------------------------
    // Concrete subclass used to exercise abstract / protected methods
    // ------------------------------------------------------------------

    /**
     * Stub model used to call into protected/abstract members. {@code loadData}
     * and {@code countWithFilter} return whatever the test wires through the
     * provided functions so assertions can inspect behaviour.
     */
    private static class TestLazyDataModel extends BaseLazyDataModel<String> {
        java.util.function.BiFunction<FilterDTO, Pageable, Page<String>> loader = (f, p) -> new PageImpl<>(List.of());
        java.util.function.ToIntFunction<FilterDTO> counter = f -> 0;

        @Override
        protected Page<String> loadData(FilterDTO filter, Pageable pageable) {
            return loader.apply(filter, pageable);
        }

        @Override
        protected int countWithFilter(FilterDTO filters) {
            return counter.applyAsInt(filters);
        }
    }

    // ------------------------------------------------------------------
    // getDefaultSortDTO / getFieldMapping defaults
    // ------------------------------------------------------------------

    @Test
    void getDefaultSortDTO_isEmptyByDefault() {
        TestLazyDataModel model = new TestLazyDataModel();
        assertTrue(model.getDefaultSortDTO().isEmpty());
    }

    @Test
    void getFieldMapping_isEmptyByDefault() {
        TestLazyDataModel model = new TestLazyDataModel();
        assertTrue(model.getFieldMapping().isEmpty());
    }

    // ------------------------------------------------------------------
    // deepCopyFilterMetaMap / deepCopySortMetaMap
    // ------------------------------------------------------------------

    @Test
    void deepCopyFilterMetaMap_copiesEntriesWithSameValues() {
        Map<String, FilterMeta> original = new HashMap<>();
        FilterMeta meta = FilterMeta.builder()
                .field("f")
                .filterValue("v")
                .matchMode(MatchMode.CONTAINS)
                .build();
        original.put("k", meta);

        Map<String, FilterMeta> copy = BaseLazyDataModel.deepCopyFilterMetaMap(original);

        assertEquals(1, copy.size());
        FilterMeta copied = copy.get("k");
        assertNotSame(meta, copied);
        assertEquals(meta.getField(), copied.getField());
        assertEquals(meta.getFilterValue(), copied.getFilterValue());
        assertEquals(meta.getMatchMode(), copied.getMatchMode());
    }

    @Test
    void deepCopyFilterMetaMap_emptyMapYieldsEmptyMap() {
        Map<String, FilterMeta> copy = BaseLazyDataModel.deepCopyFilterMetaMap(new HashMap<>());
        assertTrue(copy.isEmpty());
    }

    @Test
    void deepCopySortMetaMap_copiesEntriesWithSameValues() {
        Map<String, SortMeta> original = new HashMap<>();
        SortMeta meta = SortMeta.builder().field("f").order(SortOrder.DESCENDING).build();
        original.put("k", meta);

        Map<String, SortMeta> copy = BaseLazyDataModel.deepCopySortMetaMap(original);

        assertEquals(1, copy.size());
        SortMeta copied = copy.get("k");
        assertNotSame(meta, copied);
        assertEquals(meta.getField(), copied.getField());
        assertEquals(meta.getOrder(), copied.getOrder());
    }

    @Test
    void deepCopySortMetaMap_emptyMapYieldsEmptyMap() {
        Map<String, SortMeta> copy = BaseLazyDataModel.deepCopySortMetaMap(new HashMap<>());
        assertTrue(copy.isEmpty());
    }

    // ------------------------------------------------------------------
    // updateCache / resetCache
    // ------------------------------------------------------------------

    @Test
    void updateCache_storesPagingFilteringSortingAndContent() {
        TestLazyDataModel model = new TestLazyDataModel();
        Page<String> result = new PageImpl<>(List.of("a", "b"), Pageable.ofSize(10), 27);
        filterBy = new HashMap<>();
        sortBy = new HashMap<>();

        model.updateCache(result, filterBy, sortBy, 5, 10);

        assertEquals(List.of("a", "b"), model.getQueryResult());
        assertEquals(5, model.getCachedFirst());
        assertEquals(10, model.getCachedPageSize());
        assertEquals(27, model.getCachedRowCount());
    }

    @Test
    void resetCache_clearsQueryResult() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setQueryResult(new ArrayList<>(List.of("a")));

        model.resetCache();

        assertNull(model.getQueryResult());
    }

    // ------------------------------------------------------------------
    // count(map) — globalFilter and other entries
    // ------------------------------------------------------------------

    @Test
    void count_filteringDisabled_passesEmptyFilterDTO() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setColumnFilteringEnabled(false);
        Map<String, FilterMeta> incoming = new HashMap<>();
        incoming.put("ignored", FilterMeta.builder().field("ignored").filterValue("v").build());

        Set<String> seenColumns = new HashSet<>();
        model.counter = filter -> {
            seenColumns.addAll(filter.getColumns());
            return 7;
        };

        int count = model.count(incoming);

        assertEquals(7, count);
        assertTrue(seenColumns.isEmpty(), "filtering disabled → no column passed to count");
    }

    @Test
    void count_globalFilter_remappedToGlobalFilterKey() {
        TestLazyDataModel model = new TestLazyDataModel();
        Map<String, FilterMeta> incoming = new HashMap<>();
        incoming.put("globalFilter",
                FilterMeta.builder().field("globalFilter").filterValue("needle").matchMode(MatchMode.GLOBAL).build());

        Set<String> seenColumns = new HashSet<>();
        model.counter = filter -> {
            seenColumns.addAll(filter.getColumns());
            return 3;
        };

        assertEquals(3, model.count(incoming));
        assertTrue(seenColumns.contains(FilterDTO.GLOBAL_FILTER_KEY));
    }

    // ------------------------------------------------------------------
    // load — cache hit / cache miss / filtering disabled / closure capture
    // ------------------------------------------------------------------

    @Test
    void load_returnsCachedResultWhenSameCriteria() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setColumnFilteringEnabled(false);
        model.setCachedFirst(0);
        model.setCachedPageSize(10);
        model.setCachedRowCount(99);
        List<String> cached = new ArrayList<>(List.of("x", "y"));
        model.setQueryResult(cached);

        boolean[] loaderInvoked = {false};
        model.loader = (f, p) -> {
            loaderInvoked[0] = true;
            return new PageImpl<>(List.of());
        };

        List<String> result = model.load(0, 10, new HashMap<>(), new HashMap<>());

        assertSame(cached, result);
        assertEquals(99, model.getRowCount());
        assertFalse(loaderInvoked[0], "loader must not be called on cache hit");
    }

    @Test
    void load_filteringDisabled_dropsFilterByMap() {
        TestLazyDataModel model = new TestLazyDataModel() {
            @Override
            protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
                if (filterBy != null) {
                    for (Map.Entry<String, FilterMeta> e : filterBy.entrySet()) {
                        filterDTO.add(e.getKey(), e.getValue().getFilterValue(), FilterDTO.FilterType.CONTAINS);
                    }
                }
            }

            @Override
            protected void prepareSortDTO(Map<String, SortMeta> sortBy, SortDTO sortDTO) {
                // no-op
            }
        };
        model.setColumnFilteringEnabled(false);

        Set<String> appliedColumns = new HashSet<>();
        model.loader = (f, p) -> {
            appliedColumns.addAll(f.getColumns());
            return new PageImpl<>(List.of());
        };

        filterBy = new HashMap<>();
        filterBy.put("ignored", FilterMeta.builder().field("ignored").filterValue("v").build());

        model.load(0, 10, new HashMap<>(), filterBy);

        assertTrue(appliedColumns.isEmpty());
    }

    @Test
    void load_capturesClosureSnapshot_fromFilterDTO() {
        TestLazyDataModel model = new TestLazyDataModel() {
            @Override
            protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
                // no-op
            }

            @Override
            protected void prepareSortDTO(Map<String, SortMeta> sortBy, SortDTO sortDTO) {
                // no-op
            }
        };
        Set<Long> closure = Set.of(1L, 2L);
        Set<Long> matches = Set.of(3L);
        model.loader = (f, p) -> {
            f.setAncestorClosure(closure);
            f.setMatchIds(matches);
            return new PageImpl<>(List.of("a"), Pageable.ofSize(10), 1);
        };

        model.load(0, 10, new HashMap<>(), new HashMap<>());

        assertEquals(closure, model.getAncestorClosure());
        assertEquals(matches, model.getMatchIds());
    }

    @Test
    void load_capturesClosureSnapshot_wrapsNonSetCollection() {
        TestLazyDataModel model = new TestLazyDataModel() {
            @Override
            protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
                // no-op
            }

            @Override
            protected void prepareSortDTO(Map<String, SortMeta> sortBy, SortDTO sortDTO) {
                // no-op
            }
        };
        model.loader = (f, p) -> {
            // Collection that is not a Set — must be wrapped into a HashSet.
            f.setAncestorClosure(new ArrayList<>(List.of(1L, 2L)));
            return new PageImpl<>(List.of("a"), Pageable.ofSize(10), 1);
        };

        model.load(0, 10, new HashMap<>(), new HashMap<>());

        assertEquals(Set.of(1L, 2L), model.getAncestorClosure());
    }

    @Test
    void load_capturesClosureSnapshot_nullCollection_clearsClosure() {
        TestLazyDataModel model = new TestLazyDataModel() {
            @Override
            protected void prepareFilterDTO(Map<String, FilterMeta> filterBy, FilterDTO filterDTO) {
                // no-op
            }

            @Override
            protected void prepareSortDTO(Map<String, SortMeta> sortBy, SortDTO sortDTO) {
                // no-op
            }
        };
        model.setAncestorClosure(Set.of(1L));
        model.loader = (f, p) -> new PageImpl<>(List.of(), Pageable.ofSize(10), 0);

        model.load(0, 10, new HashMap<>(), new HashMap<>());

        assertNull(model.getAncestorClosure());
    }

    // ------------------------------------------------------------------
    // Pagination helpers
    // ------------------------------------------------------------------

    @Test
    void getFirstIndexOnPage_returnsFirstPlusOne() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setFirst(20);
        assertEquals(21, model.getFirstIndexOnPage());
    }

    @Test
    void getLastIndexOnPage_truncatesAtRowCount() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setFirst(20);
        model.setPageSizeState(10);
        model.setRowCount(25);

        assertEquals(25, model.getLastIndexOnPage());
    }

    @Test
    void getLastIndexOnPage_returnsFullPageWhenRoomLeft() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setFirst(20);
        model.setPageSizeState(10);
        model.setRowCount(100);

        assertEquals(30, model.getLastIndexOnPage());
    }

    // ------------------------------------------------------------------
    // addRowToModel
    // ------------------------------------------------------------------

    @Test
    void addRowToModel_emptyWrappedData_addsAtStartAndIncrementsTotals() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setRowCount(0);
        model.setCachedRowCount(0);
        model.setPageSizeState(10);

        model.addRowToModel("new");

        assertEquals(1, model.getRowCount());
        assertEquals(1, model.getCachedRowCount());
        assertEquals(List.of("new"), model.getQueryResult());
        assertEquals(List.of("new"), model.getWrappedData());
    }

    @Test
    void addRowToModel_existingPage_prependsAndTrimsToPageSize() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setRowCount(20);
        model.setCachedRowCount(20);
        model.setPageSizeState(3);
        model.setWrappedData(new ArrayList<>(List.of("a", "b", "c")));

        model.addRowToModel("new");

        assertEquals(21, model.getRowCount());
        assertEquals(21, model.getCachedRowCount());
        // Trimmed to 3 entries with the new one at position 0.
        assertEquals(List.of("new", "a", "b"), model.getQueryResult());
    }

    @Test
    void addRowToModel_pageNotFull_doesNotTrim() {
        TestLazyDataModel model = new TestLazyDataModel();
        model.setRowCount(2);
        model.setCachedRowCount(2);
        model.setPageSizeState(10);
        model.setWrappedData(new ArrayList<>(List.of("a", "b")));

        model.addRowToModel("new");

        assertEquals(3, model.getRowCount());
        assertEquals(List.of("new", "a", "b"), model.getQueryResult());
    }


}