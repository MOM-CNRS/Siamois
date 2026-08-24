package fr.siamois.domain.services;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.identifier.EntityIdentifierGenerator;
import fr.siamois.domain.services.identifier.GeneratedIdentifier;
import fr.siamois.domain.services.identifier.IdentifierGenerationSpec;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.ContainerRepository;
import fr.siamois.infrastructure.database.repositories.specs.ActionUnitSpec;
import fr.siamois.mapper.ContainerMapper;
import fr.siamois.utils.context.ExecutionContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContainerServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private ContainerMapper containerMapper;

    @Mock
    private UnitDefinitionService unitDefinitionService;

    @Mock
    private EntityIdentifierGenerator identifierGenerator;

    @Mock
    private ProfilePermissionService profilePermissionService;

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

        ExecutionContextHolder.set(new UserInfo(institutionDTO, new PersonDTO(), "fr"));
        lenient().when(profilePermissionService.hasProjectPermission(any(), any(), anyString())).thenReturn(true);
    }

    @AfterEach
    void clearExecutionContext() {
        ExecutionContextHolder.clear();
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

    @Test
    void save_existingContainer_savesAndConvertsWithoutGeneratingIdentifier() {
        when(containerMapper.invertConvert(containerDTO)).thenReturn(container);
        when(containerRepository.save(container)).thenReturn(container);
        when(containerMapper.convert(container)).thenReturn(containerDTO);

        ContainerDTO result = containerService.save(containerDTO);

        assertSame(containerDTO, result);
        verify(containerRepository).save(container);
        verify(identifierGenerator).generateIdentifierIfRequired(eq(container), any());
    }

    @Test
    void save_throwsForbidden_whenNoExecutionContext() {
        when(containerMapper.invertConvert(containerDTO)).thenReturn(container);
        ExecutionContextHolder.clear();

        assertThrows(fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException.class,
                () -> containerService.save(containerDTO));

        verify(containerRepository, never()).save(any(Container.class));
    }

    @Test
    void save_throwsForbidden_whenPermissionDenied() {
        when(containerMapper.invertConvert(containerDTO)).thenReturn(container);
        when(profilePermissionService.hasProjectPermission(any(), any(), anyString())).thenReturn(false);

        assertThrows(fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException.class,
                () -> containerService.save(containerDTO));

        verify(containerRepository, never()).save(any(Container.class));
    }

    @Test
    void save_checksPermissionOnTheContainerActionUnit() {
        ContainerDTO newContainerDTO = new ContainerDTO();
        Container newContainer = new Container();
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(7L);
        newContainer.setActionUnit(actionUnit);
        when(containerMapper.invertConvert(newContainerDTO)).thenReturn(newContainer);
        when(containerRepository.save(newContainer)).thenReturn(newContainer);
        when(containerMapper.convert(newContainer)).thenReturn(new ContainerDTO());

        containerService.save(newContainerDTO);

        verify(profilePermissionService).hasProjectPermission(any(), eq(7L), eq(PermissionConstants.PROJECT_EDIT_CONTAINERS));
    }

    @Test
    void save_newContainer_resolvesParentAndGeneratesIdentifier() {
        ContainerDTO newContainerDTO = new ContainerDTO();
        newContainerDTO.setParentId(3L);
        Container newContainer = new Container();
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(7L);
        actionUnit.setFullIdentifier("UA-7");
        newContainer.setActionUnit(actionUnit);
        Concept type = new Concept();
        type.setId(42L);
        newContainer.setType(type);

        Container parent = new Container();
        parent.setId(3L);
        parent.setGeneratedNumber(5);
        parent.setIdentifier("CONT-005");

        ContainerDTO savedDTO = new ContainerDTO();
        savedDTO.setId(101L);
        when(containerMapper.invertConvert(newContainerDTO)).thenReturn(newContainer);
        when(containerRepository.findById(3L)).thenReturn(java.util.Optional.of(parent));
        when(identifierGenerator.generateIdentifierIfRequired(eq(newContainer), any())).thenAnswer(invocation -> {
            IdentifierGenerationSpec<Container> spec = invocation.getArgument(1);
            GeneratedIdentifier generated = new GeneratedIdentifier(6, "CONT-006");
            spec.numberSetter().accept(newContainer, generated.number());
            spec.identifierSetter().accept(newContainer, generated.value());
            return java.util.Optional.of(generated);
        });
        when(containerRepository.save(newContainer)).thenReturn(newContainer);
        when(containerMapper.convert(newContainer)).thenReturn(savedDTO);

        ContainerDTO result = containerService.save(newContainerDTO);

        assertSame(savedDTO, result);
        assertSame(parent, newContainer.getParent());
        assertEquals(6, newContainer.getGeneratedNumber());
        assertEquals("CONT-006", newContainer.getIdentifier());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<IdentifierGenerationSpec<Container>> specCaptor = ArgumentCaptor.forClass(IdentifierGenerationSpec.class);
        verify(identifierGenerator).generateIdentifierIfRequired(eq(newContainer), specCaptor.capture());
        IdentifierGenerationSpec<Container> spec = specCaptor.getValue();
        assertEquals(ConfigurableTable.CONTENANT, spec.table());
        assertTrue(spec.generationRequired().test(newContainer));
        assertSame(actionUnit, spec.actionUnit().apply(newContainer));
        assertEquals(42L, spec.typeId().apply(newContainer));
        assertEquals(5, spec.displayValues().get("NUM_PARENT").apply(newContainer));
        assertEquals("CONT-005", spec.displayValues().get("ID_PARENT").apply(newContainer));
        assertEquals("UA-7", spec.displayValues().get("ID_UA").apply(newContainer));
        assertEquals(3L, spec.partitionValues().get("PARENT_CONTAINER").apply(newContainer));

        when(containerRepository.existsByActionUnitIdAndIdentifier(7L, "CONT-006")).thenReturn(true);
        assertTrue(spec.identifierAlreadyUsed().test(newContainer, "CONT-006"));

        Container rootContainer = new Container();
        rootContainer.setActionUnit(actionUnit);
        assertNull(spec.typeId().apply(rootContainer));
        assertNull(spec.displayValues().get("NUM_PARENT").apply(rootContainer));
        assertNull(spec.displayValues().get("ID_PARENT").apply(rootContainer));
        assertNull(spec.partitionValues().get("PARENT_CONTAINER").apply(rootContainer));
        rootContainer.setId(99L);
        assertFalse(spec.generationRequired().test(rootContainer));
    }

    @Test
    void save_rejectsUnknownParent() {
        ContainerDTO newContainerDTO = new ContainerDTO();
        newContainerDTO.setParentId(404L);
        when(containerMapper.invertConvert(newContainerDTO)).thenReturn(new Container());
        when(containerRepository.findById(404L)).thenReturn(java.util.Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> containerService.save(newContainerDTO),
                "Container parent not found: 404");
        verify(containerRepository, never()).save(any());
    }

    @Test
    void save_rejectsNewContainerWithoutActionUnit() {
        ContainerDTO newContainerDTO = new ContainerDTO();
        Container newContainer = new Container();
        when(containerMapper.invertConvert(newContainerDTO)).thenReturn(newContainer);
        when(identifierGenerator.generateIdentifierIfRequired(eq(newContainer), any())).thenCallRealMethod();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> containerService.save(newContainerDTO));

        assertEquals("An action unit is required to generate a container identifier", exception.getMessage());
        verify(containerRepository, never()).save(any());
    }

    @Test
    void save_rejectsMissingMappedContainer() {
        ContainerDTO newContainerDTO = new ContainerDTO();
        when(containerMapper.invertConvert(newContainerDTO)).thenReturn(null);
        when(identifierGenerator.generateIdentifierIfRequired(isNull(), any())).thenCallRealMethod();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> containerService.save(newContainerDTO));

        assertEquals("A container is required to generate an identifier", exception.getMessage());
        verify(containerRepository, never()).save(any());
    }
}
