package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.specs.RecordingUnitSpec;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.event.RowEditEvent;
import org.primefaces.model.FilterMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingUnitLazyDataModelTest {

    @Mock
    private RecordingUnitService recordingUnitService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @Mock
    private LangBean langBean;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private RecordingUnitLazyDataModel lazyModel; // RecordingUnitLazyDataModel hérite de BaseRecordingUnitLazyDataModel

    Page<RecordingUnit> p ;
    Page<RecordingUnitDTO> pageDTO ;
    Pageable pageable;
    RecordingUnit unit1;
    RecordingUnit unit2;
    RecordingUnitDTO unit1DTO;
    RecordingUnitDTO unit2DTO;
    Institution institution;
    InstitutionDTO institutionDTO;

    @BeforeEach
    void setUp() {
        unit1 = new RecordingUnit();
        unit2 = new RecordingUnit();
        unit1DTO = new RecordingUnitDTO();
        unit2DTO = new RecordingUnitDTO();
        institution = new Institution();
        institution.setId(1L);
        institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);
        unit1.setId(1L);
        unit1.setFullIdentifier("sia-2025-1");
        unit2.setId(2L);
        unit2.setFullIdentifier("sia-2025-2");
        unit1DTO.setId(1L);
        unit1DTO.setFullIdentifier("sia-2025-1");
        unit2DTO.setId(2L);
        unit2DTO.setFullIdentifier("sia-2025-2");
        p = new PageImpl<>(List.of(unit1, unit2));
        pageDTO = new PageImpl<>(List.of(unit1DTO, unit2DTO));
        pageable = PageRequest.of(0, 10);
    }

    private RecordingUnitDTO createUnit(long id) {
        RecordingUnitDTO unit = new RecordingUnitDTO();
        unit.setId(id);
        return unit;
    }

    @Test
    void testGetRowKey_Success() {
        RecordingUnitDTO unit = createUnit(123L);
        String key = lazyModel.getRowKey(unit);
        assertEquals("123", key);
    }

    @Test
    void testGetRowKey_NullInput() {
        String key = lazyModel.getRowKey(null);
        assertNull(key);
    }

    @Test
    void testGetRowData_Success() {
        RecordingUnitDTO expectedUnit = createUnit(456L);
        List<RecordingUnitDTO> units = Arrays.asList(
                createUnit(123L),
                expectedUnit,
                createUnit(789L)
        );
        lazyModel.setWrappedData(units);

        RecordingUnitDTO result = lazyModel.getRowData("456");
        assertNotNull(result);
        assertEquals(456L, result.getId());
    }

    @Test
    void testGetRowData_NotFound() {
        List<RecordingUnitDTO> units = Arrays.asList(
                createUnit(100L),
                createUnit(200L)
        );
        lazyModel.setWrappedData(units);

        RecordingUnitDTO result = lazyModel.getRowData("300");
        assertNull(result);
    }

    @Test
    void testHandleRowEdit_successfulSave() {
        RecordingUnitDTO unit = new RecordingUnitDTO();
        unit.setFullIdentifier("RU123");

        RowEditEvent<RecordingUnitDTO> event = mock(RowEditEvent.class);
        when(event.getObject()).thenReturn(unit);

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            lazyModel.handleRowEdit(event);

            verify(recordingUnitService).save(unit);
            messageUtilsMock.verify(() ->
                    MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.updated", "RU123"));
        }
    }

    @Test
    void testHandleRowEdit_failedSave() {
        RecordingUnitDTO unit = new RecordingUnitDTO();
        unit.setFullIdentifier("RU123");

        RowEditEvent<RecordingUnitDTO> event = mock(RowEditEvent.class);
        when(event.getObject()).thenReturn(unit);

        doThrow(new FailedRecordingUnitSaveException("")).when(recordingUnitService).save(any());

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            lazyModel.handleRowEdit(event);

            verify(recordingUnitService).save(unit);
            messageUtilsMock.verify(() ->
                    MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", "RU123"));
        }
    }

    @Test
    void testSaveFieldBulk_updatesTypeAndDisplaysMessage() {
        RecordingUnitDTO r1 = new RecordingUnitDTO();
        r1.setId(1L);
        RecordingUnitDTO r2 = new RecordingUnitDTO();
        r2.setId(2L);

        ConceptDTO newType = new ConceptDTO();
        lazyModel.setBulkEditTypeValue(newType);
        lazyModel.setSelectedUnits(List.of(r1, r2));

        when(recordingUnitService.bulkUpdateType(anyList(), eq(newType))).thenReturn(2);

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            lazyModel.saveFieldBulk();

            assertSame(newType, r1.getType());
            assertSame(newType, r2.getType());
            verify(recordingUnitService).bulkUpdateType(List.of(1L, 2L), newType);
            messageUtilsMock.verify(() ->
                    MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.bulkUpdated", 2));
        }
    }

    // --- NOUVEAUX TESTS DEMANDÉS ---

    @Test
    void testGetDefaultSortDTO() {
        SortDTO sortDTO = lazyModel.getDefaultSortDTO();
        assertNotNull(sortDTO, "SortDTO should not be null");
        // On s'assure que le tri par défaut ID ASC est bien ajouté
        assertEquals(SortDTO.SortOrder.ASC, sortDTO.orderOf(RecordingUnitSpec.ID_FILTER));
    }

    @Test
    void testGetFieldMapping() {
        Map<String, String> mapping = lazyModel.getFieldMapping();
        assertNotNull(mapping);
        assertEquals("c_label", mapping.get("category"));
        assertEquals("creation_time", mapping.get("creationTime"));
        assertEquals("p_lastname", mapping.get("author"));

        // Vérification de l'immutabilité
        assertThrows(UnsupportedOperationException.class, () -> mapping.put("test", "test"));
    }

    @Test
    void testPrepareFilterDTO_EmptyOrNull() {
        FilterDTO filterDTO = mock(FilterDTO.class);

        lazyModel.prepareFilterDTO(null, filterDTO);
        verifyNoInteractions(filterDTO);

        lazyModel.prepareFilterDTO(new HashMap<>(), filterDTO);
        verifyNoInteractions(filterDTO);
    }

    @Test
    void testPrepareFilterDTO_WithFilters() {
        Map<String, FilterMeta> filterBy = new HashMap<>();

        // Préparation des métadonnées de filtre
        FilterMeta fullIdMeta = mock(FilterMeta.class);
        when(fullIdMeta.getFilterValue()).thenReturn("sia-123");
        filterBy.put(RecordingUnitSpec.FULL_IDENTIFIER, fullIdMeta);

        FilterMeta authorMeta = mock(FilterMeta.class);
        List<Long> authorIds = List.of(1L, 2L);
        when(authorMeta.getFilterValue()).thenReturn(authorIds);
        filterBy.put(RecordingUnitSpec.AUTHOR_FILTER, authorMeta);

        FilterMeta dateMeta = mock(FilterMeta.class);
        List<LocalDate> dates = List.of(LocalDate.now());
        when(dateMeta.getFilterValue()).thenReturn(dates);
        filterBy.put(RecordingUnitSpec.OPENING_DATE_FILTER, dateMeta);

        FilterDTO filterDTO = mock(FilterDTO.class);

        // Appel de la méthode protected
        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        // Vérifications
        verify(filterDTO).add(RecordingUnitSpec.FULL_IDENTIFIER, "sia-123", FilterDTO.FilterType.CONTAINS);
        verify(filterDTO).add(RecordingUnitSpec.AUTHOR_FILTER, authorIds, FilterDTO.FilterType.CONTAINS);
        verify(filterDTO).add(RecordingUnitSpec.OPENING_DATE_FILTER, dates, FilterDTO.FilterType.CONTAINS);
    }

    @Test
    void testDuplicateRow_createsCopyAndAddsToModel_Success() {
        // GIVEN
        RecordingUnitDTO original = new RecordingUnitDTO();
        original.setFullIdentifier("RU-Original");
        original.setId(1L);

        RecordingUnitDTO copied = new RecordingUnitDTO();
        copied.setId(999L);

        BaseRecordingUnitLazyDataModel spyModel = spy(lazyModel);
        doReturn(original).when(spyModel).getRowData();
        doNothing().when(spyModel).addRowToModel(any()); // Évite l'insertion dans une liste nulle du BaseLazyDataModel

        // Le service save est appelé 2 fois : 1 pour initialiser, 1 pour valider l'identifiant
        when(recordingUnitService.save(any(RecordingUnitDTO.class))).thenReturn(copied);
        when(recordingUnitService.generateFullIdentifier(any(), any())).thenReturn("RU-Copy-Generated");
        when(recordingUnitService.fullIdentifierAlreadyExistInAction(any())).thenReturn(false);

        // WHEN
        spyModel.duplicateRow();

        // THEN
        verify(recordingUnitService, times(2)).save(any(RecordingUnitDTO.class));
        verify(spyModel).addRowToModel(copied);
    }

    @Test
    void testDuplicateRow_IdentifierAlreadyExists() {
        // GIVEN
        RecordingUnitDTO original = new RecordingUnitDTO();
        original.setFullIdentifier("RU-Original");
        original.setId(1L);

        RecordingUnitDTO copied = spy(new RecordingUnitDTO()); // Spy pour vérifier resetFullIdentifier()
        copied.setId(999L);

        BaseRecordingUnitLazyDataModel spyModel = spy(lazyModel);
        doReturn(original).when(spyModel).getRowData();
        doNothing().when(spyModel).addRowToModel(any());

        when(recordingUnitService.save(any(RecordingUnitDTO.class))).thenReturn(copied);
        when(recordingUnitService.generateFullIdentifier(any(), any())).thenReturn("RU-Copy-Generated");
        when(recordingUnitService.fullIdentifierAlreadyExistInAction(any())).thenReturn(true); // Provoque le bloc IF

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            // WHEN
            spyModel.duplicateRow();

            // THEN
            verify(recordingUnitService, times(2)).save(any(RecordingUnitDTO.class));
            verify(copied).resetFullIdentifier(); // Vérifie que l'identifiant a été réinitialisé
            verify(spyModel).addRowToModel(copied);

            // Vérifie que le warning est bien envoyé via MessageUtils
            messageUtilsMock.verify(() ->
                    MessageUtils.displayWarnMessage(langBean, "recordingunit.error.identifier.alreadyExists"));
        }
    }

}