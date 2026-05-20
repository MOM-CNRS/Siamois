package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.services.ContainerService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.ui.bean.SessionSettingsBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.MatchMode;
import org.primefaces.model.SortMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerLazyDataModelTest {

    @Mock
    private ContainerService containerService;

    @Mock
    private SessionSettingsBean sessionSettings;

    private ContainerLazyDataModel lazyDataModel;
    private InstitutionDTO institutionDTO;

    @BeforeEach
    void setUp() {
        institutionDTO = new InstitutionDTO();
        institutionDTO.setId(10L);

        lazyDataModel = new ContainerLazyDataModel(containerService, sessionSettings);
    }

    @Test
    void loadData_ShouldCallServiceWithSelectedInstitution() {
        // Arrange
        FilterDTO filterDTO = new FilterDTO();
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContainerDTO> expectedPage = new PageImpl<>(List.of(new ContainerDTO()));

        when(sessionSettings.getSelectedInstitution()).thenReturn(institutionDTO);
        when(containerService.searchContainers(institutionDTO, filterDTO, pageable)).thenReturn(expectedPage);

        // Act
        Page<ContainerDTO> actualPage = lazyDataModel.loadData(filterDTO, pageable);

        // Assert
        assertNotNull(actualPage);
        assertEquals(expectedPage, actualPage);
        verify(containerService).searchContainers(institutionDTO, filterDTO, pageable);
    }

    @Test
    void countWithFilter_ShouldReturnCountFromService() {
        // Arrange
        FilterDTO filterDTO = new FilterDTO();
        when(sessionSettings.getSelectedInstitution()).thenReturn(institutionDTO);
        when(containerService.countSearchResults(institutionDTO, filterDTO)).thenReturn(42);

        // Act
        int count = lazyDataModel.countWithFilter(filterDTO);

        // Assert
        assertEquals(42, count);
        verify(containerService).countSearchResults(institutionDTO, filterDTO);
    }

    @Test
    void prepareFilterDTO_WithNullOrEmptyFilters_ShouldLeaveFilterDTOEmpty() {
        // Arrange
        FilterDTO filterDTO = new FilterDTO();

        // Act
        lazyDataModel.prepareFilterDTO(null, filterDTO);
        lazyDataModel.prepareFilterDTO(Collections.emptyMap(), filterDTO);

        // Assert
        assertFalse(filterDTO.hasUserFilters());
    }

    @Test
    void prepareFilterDTO_WithValidNameAndGlobalFilters_ShouldPopulateFilterDTO() {
        // Arrange
        FilterDTO filterDTO = new FilterDTO();
        Map<String, FilterMeta> filterBy = new HashMap<>();

        FilterMeta nameMeta = FilterMeta.builder()
                .field(ActionUnitSpec.NAME_FILTER)
                .filterValue("MyContainer")
                .matchMode(MatchMode.CONTAINS)
                .build();

        FilterMeta globalMeta = FilterMeta.builder()
                .field(ActionUnitSpec.GLOBAL_FILTER)
                .filterValue("GlobalSearchText")
                .matchMode(MatchMode.CONTAINS)
                .build();

        filterBy.put(ActionUnitSpec.NAME_FILTER, nameMeta);
        filterBy.put(ActionUnitSpec.GLOBAL_FILTER, globalMeta);

        // Act
        lazyDataModel.prepareFilterDTO(filterBy, filterDTO);

        // Assert
        assertTrue(filterDTO.hasUserFilters());
        assertTrue(filterDTO.containsColumn(ActionUnitSpec.NAME_FILTER));
        assertTrue(filterDTO.containsColumn(FilterDTO.GLOBAL_FILTER_KEY));

        assertEquals("MyContainer", filterDTO.valueOfAsString(ActionUnitSpec.NAME_FILTER));
        assertEquals("GlobalSearchText", filterDTO.valueOfAsString(FilterDTO.GLOBAL_FILTER_KEY));

        assertEquals(FilterDTO.FilterType.CONTAINS, filterDTO.filterOf(ActionUnitSpec.NAME_FILTER).getType());
        assertEquals(FilterDTO.FilterType.CONTAINS, filterDTO.filterOf(FilterDTO.GLOBAL_FILTER_KEY).getType());
    }

    @Test
    void prepareSortDTO_WithNullOrEmptySorts_ShouldLeaveSortDTOUnchanged() {
        // Arrange
        SortDTO sortDTO = new SortDTO();

        // Act
        lazyDataModel.prepareSortDTO(null, sortDTO);
        lazyDataModel.prepareSortDTO(Collections.emptyMap(), sortDTO);

        // Assert
        assertTrue(sortDTO.isEmpty());
    }

    @Test
    void prepareSortDTO_WithPrimefacesAscendingSort_ShouldMapToAscSortDTO() {
        // Arrange
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();

        SortMeta nameSortMeta = SortMeta.builder()
                .field(ActionUnitSpec.NAME_FILTER)
                .order(org.primefaces.model.SortOrder.ASCENDING)
                .build();
        sortBy.put(ActionUnitSpec.NAME_FILTER, nameSortMeta);

        // Act
        lazyDataModel.prepareSortDTO(sortBy, sortDTO);

        // Assert
        assertFalse(sortDTO.isEmpty());
        assertTrue(sortDTO.getAttributeNames().contains(ActionUnitSpec.NAME_FILTER));
        assertEquals(SortDTO.SortOrder.ASC, sortDTO.orderOf(ActionUnitSpec.NAME_FILTER));
    }

    @Test
    void prepareSortDTO_WithPrimefacesDescendingSort_ShouldMapToDescSortDTO() {
        // Arrange
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();

        SortMeta nameSortMeta = SortMeta.builder()
                .field(ActionUnitSpec.NAME_FILTER)
                .order(org.primefaces.model.SortOrder.DESCENDING)
                .build();
        sortBy.put(ActionUnitSpec.NAME_FILTER, nameSortMeta);

        // Act
        lazyDataModel.prepareSortDTO(sortBy, sortDTO);

        // Assert
        assertFalse(sortDTO.isEmpty());
        assertEquals(SortDTO.SortOrder.DESC, sortDTO.orderOf(ActionUnitSpec.NAME_FILTER));
    }
}