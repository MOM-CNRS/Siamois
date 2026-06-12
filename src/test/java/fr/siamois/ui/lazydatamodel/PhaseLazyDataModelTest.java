package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.PhaseService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
class PhaseLazyDataModelTest {

    @Mock
    private PhaseService phaseService;
    @Mock
    private SessionSettingsBean sessionSettings;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private PhaseLazyDataModel lazyModel;

    private InstitutionDTO institution;
    private PhaseDTO phase1;
    private PhaseDTO phase2;
    private Page<PhaseDTO> page;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        lazyModel = new PhaseLazyDataModel(phaseService, sessionSettings);

        institution = new InstitutionDTO();
        institution.setId(1L);

        phase1 = new PhaseDTO();
        phase1.setId(10L);
        phase2 = new PhaseDTO();
        phase2.setId(20L);

        page = new PageImpl<>(List.of(phase1, phase2));
        pageable = PageRequest.of(0, 10);
    }

    // ------------------------------------------------------------------
    // loadData
    // ------------------------------------------------------------------

    @Test
    void loadData_delegatesToServiceWithSelectedInstitution() {
        FilterDTO filter = new FilterDTO(false);
        when(sessionSettings.getSelectedInstitution()).thenReturn(institution);
        when(phaseService.searchPhases(eq(institution), same(filter), same(pageable))).thenReturn(page);

        Page<PhaseDTO> result = lazyModel.loadData(filter, pageable);

        assertSame(page, result);
        verify(phaseService).searchPhases(eq(institution), same(filter), pageableCaptor.capture());
        assertSame(pageable, pageableCaptor.getValue());
    }

    // ------------------------------------------------------------------
    // countWithFilter
    // ------------------------------------------------------------------

    @Test
    void countWithFilter_delegatesToServiceAndReturnsCount() {
        FilterDTO filter = new FilterDTO(false);
        when(sessionSettings.getSelectedInstitution()).thenReturn(institution);
        when(phaseService.countSearchResults(institution, filter)).thenReturn(7);

        int count = lazyModel.countWithFilter(filter);

        assertEquals(7, count);
        verify(phaseService).countSearchResults(institution, filter);
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
                FilterMeta.builder().field(ActionUnitSpec.NAME_FILTER).filterValue("test").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.containsColumn(ActionUnitSpec.NAME_FILTER));
        assertEquals("test", filterDTO.valueOfAsString(ActionUnitSpec.NAME_FILTER));
        assertEquals(FilterDTO.FilterType.CONTAINS, filterDTO.filterOf(ActionUnitSpec.NAME_FILTER).getType());
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
                FilterMeta.builder().field(ActionUnitSpec.GLOBAL_FILTER).filterValue("search").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.containsColumn(FilterDTO.GLOBAL_FILTER_KEY));
        assertEquals("search", filterDTO.valueOfAsString(FilterDTO.GLOBAL_FILTER_KEY));
        assertEquals(FilterDTO.FilterType.CONTAINS, filterDTO.filterOf(FilterDTO.GLOBAL_FILTER_KEY).getType());
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
                FilterMeta.builder().field(ActionUnitSpec.NAME_FILTER).filterValue("p1").build());
        filterBy.put(ActionUnitSpec.GLOBAL_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.GLOBAL_FILTER).filterValue("p2").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertEquals("p1", filterDTO.valueOfAsString(ActionUnitSpec.NAME_FILTER));
        assertEquals("p2", filterDTO.valueOfAsString(FilterDTO.GLOBAL_FILTER_KEY));
    }

    @Test
    void prepareFilterDTO_unrelatedKey_isIgnored() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put("unknown",
                FilterMeta.builder().field("unknown").filterValue("val").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
    }

    @Test
    void prepareFilterDTO_filterValueIsNotAString_isCoercedViaToString() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(ActionUnitSpec.NAME_FILTER,
                FilterMeta.builder().field(ActionUnitSpec.NAME_FILTER).filterValue(42).build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertEquals("42", filterDTO.valueOfAsString(ActionUnitSpec.NAME_FILTER));
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

    @Test
    void prepareSortDTO_doesNotInteractWithService() {
        SortDTO sortDTO = new SortDTO();

        lazyModel.prepareSortDTO(null, sortDTO);

        verifyNoInteractions(phaseService);
        verifyNoInteractions(sessionSettings);
    }

    // ------------------------------------------------------------------
    // getRowKey (inherited from BasePhaseLazyDataModel)
    // ------------------------------------------------------------------

    @Test
    void getRowKey_nonNullEntity_returnsIdAsString() {
        assertEquals("10", lazyModel.getRowKey(phase1));
    }

    @Test
    void getRowKey_nullEntity_returnsNull() {
        assertNull(lazyModel.getRowKey(null));
    }
}
