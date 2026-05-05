package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionUnitLazyDataModelTest {

    @Mock
    private ActionUnitService actionUnitService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @Mock
    @SuppressWarnings("unused")
    private LangBean langBean;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private ActionUnitLazyDataModel lazyModel;

    Page<ActionUnitDTO> p ;
    Pageable pageable;
    ActionUnitDTO unit1;
    ActionUnitDTO unit2;
    InstitutionDTO institution;


    @BeforeEach
    void setUp() {
        unit1 = new ActionUnitDTO();
        unit2 = new ActionUnitDTO();
        institution = new InstitutionDTO();
        institution.setId(1L);
        unit1.setId(1L);
        unit1.setName("Unit 1");
        unit2.setId(2L);
        p = new PageImpl<>(List.of(unit1, unit2));
        pageable = PageRequest.of(0, 10);
    }

    // ------------------------------------------------------------------
    // loadData
    // ------------------------------------------------------------------

    @Test
    void loadData_delegatesToServiceWithSelectedInstitution() {
        FilterDTO filter = new FilterDTO(false);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institution);
        when(actionUnitService.searchActionUnits(eq(institution), same(filter), same(pageable))).thenReturn(p);

        Page<ActionUnitDTO> result = lazyModel.loadData(filter, pageable);

        assertSame(p, result);
        verify(actionUnitService).searchActionUnits(eq(institution), same(filter), pageableCaptor.capture());
        assertSame(pageable, pageableCaptor.getValue());
    }

    // ------------------------------------------------------------------
    // countWithFilter
    // ------------------------------------------------------------------

    @Test
    void countWithFilter_delegatesToServiceAndReturnsCount() {
        FilterDTO filter = new FilterDTO(false);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institution);
        when(actionUnitService.countSearchResults(institution, filter)).thenReturn(42);

        int count = lazyModel.countWithFilter(filter);

        assertEquals(42, count);
        verify(actionUnitService).countSearchResults(institution, filter);
    }

    // ------------------------------------------------------------------
    // prepareFilterDTO
    // ------------------------------------------------------------------

    @Test
    void prepareFilterDTO_nullFilterBy_addsNothing() {
        FilterDTO filterDTO = new FilterDTO(false);

        lazyModel.prepareFilterDTO(null, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_emptyFilterBy_addsNothing() {
        FilterDTO filterDTO = new FilterDTO(false);

        lazyModel.prepareFilterDTO(new HashMap<>(), filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_nameFilterWithValue_isAdded() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(ActionUnitSpec.NAME_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.NAME_FILTER).filterValue("foo").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.containsColumn(ActionUnitSpec.NAME_FILTER));
        assertEquals("foo", filterDTO.valueOfAsString(ActionUnitSpec.NAME_FILTER));
        assertEquals(FilterDTO.FilterType.CONTAINS,
                filterDTO.filterOf(ActionUnitSpec.NAME_FILTER).getType());
    }

    @Test
    void prepareFilterDTO_nameFilterWithNullValue_isSkipped() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(ActionUnitSpec.NAME_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.NAME_FILTER).build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_globalFilterWithValue_isAddedUnderGlobalKey() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(ActionUnitSpec.GLOBAL_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.GLOBAL_FILTER).filterValue("bar").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.containsColumn(FilterDTO.GLOBAL_FILTER_KEY));
        assertEquals("bar", filterDTO.valueOfAsString(FilterDTO.GLOBAL_FILTER_KEY));
        assertEquals(FilterDTO.FilterType.CONTAINS,
                filterDTO.filterOf(FilterDTO.GLOBAL_FILTER_KEY).getType());
    }

    @Test
    void prepareFilterDTO_globalFilterWithNullValue_isSkipped() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(ActionUnitSpec.GLOBAL_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.GLOBAL_FILTER).build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_nameAndGlobalFilters_areBothAdded() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(ActionUnitSpec.NAME_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.NAME_FILTER).filterValue("foo").build());
        filterBy.put(ActionUnitSpec.GLOBAL_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.GLOBAL_FILTER).filterValue("bar").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertEquals("foo", filterDTO.valueOfAsString(ActionUnitSpec.NAME_FILTER));
        assertEquals("bar", filterDTO.valueOfAsString(FilterDTO.GLOBAL_FILTER_KEY));
    }

    @Test
    void prepareFilterDTO_unrelatedKey_isIgnored() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put("unknown",
                FilterMeta.builder().field("unknown").filterValue("baz").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_filterValueIsNotAString_isCoercedViaToString() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(ActionUnitSpec.NAME_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.NAME_FILTER).filterValue(123).build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertEquals("123", filterDTO.valueOfAsString(ActionUnitSpec.NAME_FILTER));
    }

    // ------------------------------------------------------------------
    // prepareSortDTO
    // ------------------------------------------------------------------

    @Test
    void prepareSortDTO_nullSortBy_addsNothing() {
        SortDTO sortDTO = new SortDTO();

        lazyModel.prepareSortDTO(null, sortDTO);

        assertTrue(sortDTO.isEmpty());
    }

    @Test
    void prepareSortDTO_emptySortBy_addsNothing() {
        SortDTO sortDTO = new SortDTO();

        lazyModel.prepareSortDTO(new HashMap<>(), sortDTO);

        assertTrue(sortDTO.isEmpty());
    }

    @Test
    void prepareSortDTO_nameAscending_isAdded() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put(ActionUnitSpec.NAME_FILTER,
                SortMeta.builder().field(ActionUnitSpec.NAME_FILTER).order(SortOrder.ASCENDING).build());

        lazyModel.prepareSortDTO(sortBy, sortDTO);

        assertEquals(SortDTO.SortOrder.ASC, sortDTO.orderOf(ActionUnitSpec.NAME_FILTER));
    }

    @Test
    void prepareSortDTO_nameDescending_isAdded() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put(ActionUnitSpec.NAME_FILTER,
                SortMeta.builder().field(ActionUnitSpec.NAME_FILTER).order(SortOrder.DESCENDING).build());

        lazyModel.prepareSortDTO(sortBy, sortDTO);

        assertEquals(SortDTO.SortOrder.DESC, sortDTO.orderOf(ActionUnitSpec.NAME_FILTER));
    }

    @Test
    void prepareSortDTO_unrelatedKey_isIgnored() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put("unknown",
                SortMeta.builder().field("unknown").order(SortOrder.ASCENDING).build());

        lazyModel.prepareSortDTO(sortBy, sortDTO);

        assertTrue(sortDTO.isEmpty());
    }

    // ------------------------------------------------------------------
    // Inherited from BaseActionUnitLazyDataModel
    // ------------------------------------------------------------------

    @Test
    void getRowKey_nonNullEntity_returnsIdAsString() {
        assertEquals("1", lazyModel.getRowKey(unit1));
    }

    @Test
    void getRowKey_nullEntity_returnsNull() {
        assertNull(lazyModel.getRowKey(null));
    }

    @Test
    void prepareSortDTO_doesNotInteractWithService() {
        SortDTO sortDTO = new SortDTO();
        lazyModel.prepareSortDTO(null, sortDTO);

        verifyNoInteractions(actionUnitService);
        verifyNoInteractions(sessionSettingsBean);
    }
}
