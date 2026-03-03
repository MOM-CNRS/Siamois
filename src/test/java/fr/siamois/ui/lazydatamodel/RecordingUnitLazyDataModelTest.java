package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.recordingunit.RecordingUnitService;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.ui.bean.LangBean;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.utils.MessageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.primefaces.event.RowEditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;

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
    private RecordingUnitLazyDataModel lazyModel;

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

    @Test
    void loadActionUnits_Success() {

        lazyModel = new RecordingUnitLazyDataModel(recordingUnitService,sessionSettingsBean,langBean);

        // Arrange
        when(recordingUnitService.findAllByInstitutionAndByFullIdentifierContainingAndByCategoriesAndByGlobalContaining(
                any(Long.class),
                any(String.class),
                any(Long[].class),
                any(String.class),
                any(String.class),
                any(Pageable.class)
        )).thenReturn(pageDTO);
        when(sessionSettingsBean.getSelectedInstitution()).thenReturn(institutionDTO);
        when(langBean.getLanguageCode()).thenReturn("en");

        // Act
        Page<RecordingUnitDTO> actualResult = lazyModel.loadData("null",
                new Long[2],new Long[2], "null", pageable);

        // Assert
        // Assert
        assertEquals(unit1DTO, actualResult.getContent().get(0));
        assertEquals(unit2DTO, actualResult.getContent().get(1));
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
            // WHEN
            lazyModel.handleRowEdit(event);


            // THEN
            verify(recordingUnitService).save(eq(unit));

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
            // WHEN
            lazyModel.handleRowEdit(event);


            // THEN
            verify(recordingUnitService).save(eq(unit));

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

            // Confirm both were updated
            assertSame(newType, r1.getType());
            assertSame(newType, r2.getType());

            verify(recordingUnitService).bulkUpdateType(List.of(1L, 2L), newType);

            messageUtilsMock.verify(() ->
                    MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.bulkUpdated", 2));
        }


    }

    @Test
    void testDuplicateRow_createsCopyAndAddsToModel() {
        // GIVEN
        RecordingUnitDTO original = new RecordingUnitDTO();
        original.setFullIdentifier("RU-Original");
        original.setId(1L);
        original.setType(new ConceptDTO());

        RecordingUnitDTO copied = new RecordingUnitDTO(original);
        copied.setId(999L);
        copied.setIdentifier("1");

        // Create spy of lazyModel so we can override getWrappedData() and getRowData()
        BaseRecordingUnitLazyDataModel spyModel = spy(lazyModel);

        // Mock getWrappedData() to return list containing the original
        doReturn(List.of(original)).when(spyModel).getWrappedData();

        // Mock getRowData() to return the original when called with its ID as a string
        doReturn(original).when(spyModel).getRowData();

        // Mock service behavior
        when(recordingUnitService.save(any())).thenReturn(copied);

        // WHEN
        spyModel.duplicateRow();

        // THEN
        assertEquals(original.getType(), copied.getType());
        verify(recordingUnitService, times(1)).save(any());

    }








}