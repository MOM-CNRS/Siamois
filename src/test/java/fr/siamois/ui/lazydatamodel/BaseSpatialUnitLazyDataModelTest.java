package fr.siamois.ui.lazydatamodel;

import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.SpatialUnitSpec;
import org.junit.jupiter.api.Test;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseSpatialUnitLazyDataModelTest {

    /**
     * Concrete subclass for exercising abstract / protected hooks. Both
     * {@code loadData} and {@code countWithFilter} return constants so
     * assertions can focus on the behaviour declared in
     * {@link BaseSpatialUnitLazyDataModel}.
     */
    private static class TestModel extends BaseSpatialUnitLazyDataModel {
        @Override
        protected Page<SpatialUnitDTO> loadData(FilterDTO filter, Pageable pageable) {
            return new PageImpl<>(List.of());
        }

        @Override
        protected int countWithFilter(FilterDTO filters) {
            return 0;
        }
    }

    private final TestModel model = new TestModel();

    // ------------------------------------------------------------------
    // getRowKey
    // ------------------------------------------------------------------

    @Test
    void getRowKey_nonNull_returnsIdAsString() {
        SpatialUnitDTO dto = new SpatialUnitDTO();
        dto.setId(42L);
        assertEquals("42", model.getRowKey(dto));
    }

    @Test
    void getRowKey_null_returnsNull() {
        assertNull(model.getRowKey(null));
    }

    // ------------------------------------------------------------------
    // getDefaultSortDTO / getFieldMapping
    // ------------------------------------------------------------------

    @Test
    void getDefaultSortDTO_sortsOnIdAscending() {
        SortDTO sortDTO = model.getDefaultSortDTO();
        assertEquals(SortDTO.SortOrder.ASC, sortDTO.orderOf(SpatialUnitSpec.ID_FILTER));
    }

    @Test
    void getFieldMapping_containsExpectedAliases() {
        Map<String, String> mapping = model.getFieldMapping();
        assertEquals("c_label", mapping.get("category"));
        assertEquals("creation_time", mapping.get("creationTime"));
        assertEquals("p_lastname", mapping.get("author"));
    }

    @Test
    void getFieldMapping_isSharedAcrossInstances() {
        BaseSpatialUnitLazyDataModel other = new TestModel();
        assertSame(model.getFieldMapping(), other.getFieldMapping());
    }

    // ------------------------------------------------------------------
    // prepareFilterDTO
    // ------------------------------------------------------------------

    @Test
    void prepareFilterDTO_null_addsNothing() {
        FilterDTO filterDTO = new FilterDTO(false);
        model.prepareFilterDTO(null, filterDTO);
        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_empty_addsNothing() {
        FilterDTO filterDTO = new FilterDTO(false);
        model.prepareFilterDTO(new HashMap<>(), filterDTO);
        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_nameWithValue_isAdded() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(SpatialUnitSpec.NAME_FILTER,
                FilterMeta.builder().field(SpatialUnitSpec.NAME_FILTER).filterValue("foo").build());

        model.prepareFilterDTO(filterBy, filterDTO);

        assertEquals("foo", filterDTO.valueOfAsString(SpatialUnitSpec.NAME_FILTER));
        assertEquals(FilterDTO.FilterType.CONTAINS,
                filterDTO.filterOf(SpatialUnitSpec.NAME_FILTER).getType());
    }

    @Test
    void prepareFilterDTO_nameNullValue_isSkipped() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(SpatialUnitSpec.NAME_FILTER,
                FilterMeta.builder().field(SpatialUnitSpec.NAME_FILTER).build());

        model.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_categoryWithIds_isAdded() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(SpatialUnitSpec.CATEGORY_FILTER,
                FilterMeta.builder().field(SpatialUnitSpec.CATEGORY_FILTER).filterValue(List.of(1L, 2L)).build());

        model.prepareFilterDTO(filterBy, filterDTO);

        assertEquals(List.of(1L, 2L), filterDTO.valueOf(SpatialUnitSpec.CATEGORY_FILTER));
    }

    @Test
    void prepareFilterDTO_categoryEmptyList_isSkipped() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(SpatialUnitSpec.CATEGORY_FILTER,
                FilterMeta.builder().field(SpatialUnitSpec.CATEGORY_FILTER).filterValue(List.of()).build());

        model.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_categoryNonListValue_isSkipped() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(SpatialUnitSpec.CATEGORY_FILTER,
                FilterMeta.builder().field(SpatialUnitSpec.CATEGORY_FILTER).filterValue("not-a-list").build());

        model.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_nameAndCategoryTogether_areBothAdded() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(SpatialUnitSpec.NAME_FILTER,
                FilterMeta.builder().field(SpatialUnitSpec.NAME_FILTER).filterValue("foo").build());
        filterBy.put(SpatialUnitSpec.CATEGORY_FILTER,
                FilterMeta.builder().field(SpatialUnitSpec.CATEGORY_FILTER).filterValue(List.of(7L)).build());

        model.prepareFilterDTO(filterBy, filterDTO);

        assertEquals("foo", filterDTO.valueOfAsString(SpatialUnitSpec.NAME_FILTER));
        assertEquals(List.of(7L), filterDTO.valueOf(SpatialUnitSpec.CATEGORY_FILTER));
    }

    @Test
    void prepareFilterDTO_unrelatedKey_isIgnored() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put("unknown",
                FilterMeta.builder().field("unknown").filterValue("bar").build());

        model.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    // ------------------------------------------------------------------
    // prepareSortDTO
    // ------------------------------------------------------------------

    @Test
    void prepareSortDTO_null_addsNothing() {
        SortDTO sortDTO = new SortDTO();
        model.prepareSortDTO(null, sortDTO);
        assertTrue(sortDTO.isEmpty());
    }

    @Test
    void prepareSortDTO_empty_addsNothing() {
        SortDTO sortDTO = new SortDTO();
        model.prepareSortDTO(new HashMap<>(), sortDTO);
        assertTrue(sortDTO.isEmpty());
    }

    @Test
    void prepareSortDTO_nameAscending_isAdded() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put(SpatialUnitSpec.NAME_FILTER,
                SortMeta.builder().field(SpatialUnitSpec.NAME_FILTER).order(SortOrder.ASCENDING).build());

        model.prepareSortDTO(sortBy, sortDTO);

        assertEquals(SortDTO.SortOrder.ASC, sortDTO.orderOf(SpatialUnitSpec.NAME_FILTER));
    }

    @Test
    void prepareSortDTO_nameDescending_isAdded() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put(SpatialUnitSpec.NAME_FILTER,
                SortMeta.builder().field(SpatialUnitSpec.NAME_FILTER).order(SortOrder.DESCENDING).build());

        model.prepareSortDTO(sortBy, sortDTO);

        assertEquals(SortDTO.SortOrder.DESC, sortDTO.orderOf(SpatialUnitSpec.NAME_FILTER));
    }

    @Test
    void prepareSortDTO_categoryAscending_isAdded() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put(SpatialUnitSpec.CATEGORY_FILTER,
                SortMeta.builder().field(SpatialUnitSpec.CATEGORY_FILTER).order(SortOrder.ASCENDING).build());

        model.prepareSortDTO(sortBy, sortDTO);

        assertEquals(SortDTO.SortOrder.ASC, sortDTO.orderOf(SpatialUnitSpec.CATEGORY_FILTER));
    }

    @Test
    void prepareSortDTO_nameAndCategoryTogether_areBothAdded() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put(SpatialUnitSpec.NAME_FILTER,
                SortMeta.builder().field(SpatialUnitSpec.NAME_FILTER).order(SortOrder.ASCENDING).build());
        sortBy.put(SpatialUnitSpec.CATEGORY_FILTER,
                SortMeta.builder().field(SpatialUnitSpec.CATEGORY_FILTER).order(SortOrder.DESCENDING).build());

        model.prepareSortDTO(sortBy, sortDTO);

        assertEquals(SortDTO.SortOrder.ASC, sortDTO.orderOf(SpatialUnitSpec.NAME_FILTER));
        assertEquals(SortDTO.SortOrder.DESC, sortDTO.orderOf(SpatialUnitSpec.CATEGORY_FILTER));
    }

    @Test
    void prepareSortDTO_unrelatedKey_isIgnored() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put("unknown",
                SortMeta.builder().field("unknown").order(SortOrder.ASCENDING).build());

        model.prepareSortDTO(sortBy, sortDTO);

        assertTrue(sortDTO.isEmpty());
    }
}
