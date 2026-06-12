package fr.siamois.domain.services;

import fr.siamois.domain.models.container.Container;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.mapper.ContainerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private ContainerMapper containerMapper;

    @InjectMocks
    private ContainerService containerService;

    private InstitutionDTO institutionDTO;
    private Pageable pageable;
    private Container container;
    private ContainerDTO containerDTO;

    @BeforeEach
    void setUp() {
        institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);

        pageable = PageRequest.of(0, 10);

        container = new Container();
        container.setId(100L);

        containerDTO = new ContainerDTO();
        containerDTO.setId(100L);
    }

    @Test
    void searchContainers_WithNoFilters_ShouldReturnPagedContainers() {
        // Arrange
        FilterDTO filters = new FilterDTO();
        Page<Container> containerPage = new PageImpl<>(List.of(container));

        when(containerRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(containerPage);
        when(containerMapper.convert(container)).thenReturn(containerDTO);

        // Act
        Page<ContainerDTO> result = containerService.searchContainers(institutionDTO, filters, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(containerDTO, result.getContent().get(0));
        verify(containerRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchContainers_WithRootOnlyAndCachedAncestorClosure_ShouldNotQueryClosureAgain() {
        // Arrange
        FilterDTO filters = new FilterDTO();
        filters.setRootOnly(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "Box", FilterDTO.FilterType.CONTAINS);
        filters.setAncestorClosure(Set.of(100L, 200L));

        Page<Container> containerPage = new PageImpl<>(List.of(container));
        when(containerRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(containerPage);
        when(containerMapper.convert(container)).thenReturn(containerDTO);

        // Act
        Page<ContainerDTO> result = containerService.searchContainers(institutionDTO, filters, pageable);

        // Assert
        assertNotNull(result);
        verify(containerRepository, never()).findAncestorClosure(any());
        verify(containerRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchContainers_WithRootOnlyAndNoMatches_ShouldReturnEmptyDisjunctionPage() {
        // Arrange
        FilterDTO filters = new FilterDTO();
        filters.setRootOnly(true);
        filters.add(ActionUnitSpec.NAME_FILTER, "NonExistentName", FilterDTO.FilterType.CONTAINS);

        when(containerRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        Page<Container> emptyPage = new PageImpl<>(Collections.emptyList());
        when(containerRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        // Act
        Page<ContainerDTO> result = containerService.searchContainers(institutionDTO, filters, pageable);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(containerRepository, never()).findAncestorClosure(any());
    }

    @Test
    void searchContainers_WithRootOnlyAndUserFilters_ShouldComputeClosureAndStoreInFilters() {
        // Arrange
        FilterDTO filters = new FilterDTO();
        filters.setRootOnly(true);
        filters.add(ActionUnitSpec.GLOBAL_FILTER, "Warehouse", FilterDTO.FilterType.CONTAINS);

        when(containerRepository.findAll(any(Specification.class))).thenReturn(List.of(container));
        when(containerRepository.findAncestorClosure(new Long[]{100L})).thenReturn(List.of(100L, 50L));

        Page<Container> containerPage = new PageImpl<>(List.of(container));
        when(containerRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(containerPage);
        when(containerMapper.convert(container)).thenReturn(containerDTO);

        // Act
        Page<ContainerDTO> result = containerService.searchContainers(institutionDTO, filters, pageable);

        // Assert
        assertNotNull(result);
        assertNotNull(filters.getAncestorClosure());
        assertTrue(filters.getAncestorClosure().contains(50L));
        assertTrue(filters.getMatchIds().contains(100L));

        verify(containerRepository).findAll(any(Specification.class));
        verify(containerRepository).findAncestorClosure(new Long[]{100L});
    }

    @Test
    void countSearchResults_ShouldReturnValidCount() {
        // Arrange
        FilterDTO filters = new FilterDTO();
        filters.add("name", "Drawer", FilterDTO.FilterType.CONTAINS);

        when(containerRepository.count(any(Specification.class))).thenReturn(42L);

        // Act
        int count = containerService.countSearchResults(institutionDTO, filters);

        // Assert
        assertEquals(42, count);
        verify(containerRepository).count(any(Specification.class));
    }
}