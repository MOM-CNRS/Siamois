package fr.siamois.ui.lazydatamodel;

import fr.siamois.domain.models.exceptions.recordingunit.FailedRecordingUnitSaveException;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.specimen.SpecimenService;
import fr.siamois.dto.FilterDTO;
import fr.siamois.dto.SortDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.specs.SpecimenSpec;
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
import org.primefaces.model.SortMeta;
import org.primefaces.model.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpecimenUnitLazyDataModelTest {

    @Mock
    private SpecimenService specimenService;
    @Mock
    private SessionSettingsBean sessionSettingsBean;
    @Mock
    private LangBean langBean;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @InjectMocks
    private SpecimenLazyDataModel lazyModel;

    Page<Specimen> p ;
    Pageable pageable;
    Specimen unit1;
    Specimen unit2;
    Institution institution;
    RecordingUnit ru;

    Page<SpecimenDTO> pageDTO ;
    SpecimenDTO unit1dto;
    SpecimenDTO unit2dto;
    InstitutionDTO institutionDTO;
    RecordingUnitDTO ruDTO;


    @BeforeEach
    void setUp() {
        unit1 = new Specimen();
        unit2 = new Specimen();
        ru = new RecordingUnit();
        institution = new Institution();
        institution.setId(1L);
        unit1.setId(1L);
        unit1.setFullIdentifier("sia-2025-1");
        unit2.setId(2L);
        unit1.setFullIdentifier("sia-2025-2");
        p = new PageImpl<>(List.of(unit1, unit2));
        pageable = PageRequest.of(0, 10);
        unit1dto = new SpecimenDTO();
        unit2dto = new SpecimenDTO();
        ruDTO = new RecordingUnitDTO();
        institutionDTO = new InstitutionDTO();
        institutionDTO.setId(1L);
        unit1dto.setId(1L);
        unit1dto.setFullIdentifier("sia-2025-1");
        unit2dto.setId(2L);
        unit1dto.setFullIdentifier("sia-2025-2");
        pageDTO = new PageImpl<>(List.of(unit1dto, unit2dto));
    }

    private SpecimenDTO createUnit(long id) {
        SpecimenDTO unit = new SpecimenDTO();
        unit.setId(id);
        return unit;
    }


    @Test
    void testGetRowKey_Success() {
        SpecimenDTO unit = createUnit(123L);
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
        SpecimenDTO expectedUnit = createUnit(456L);
        List<SpecimenDTO> units = Arrays.asList(
                createUnit(123L),
                expectedUnit,
                createUnit(789L)
        );
        lazyModel.setWrappedData(units);

        SpecimenDTO result = lazyModel.getRowData("456");
        assertNotNull(result);
        assertEquals(456L, result.getId());
    }

    @Test
    void testGetRowData_NotFound() {
        List<SpecimenDTO> units = Arrays.asList(
                createUnit(100L),
                createUnit(200L)
        );
        lazyModel.setWrappedData(units);

        SpecimenDTO result = lazyModel.getRowData("300");
        assertNull(result);
    }

    @Test
    void testHandleRowEdit_successfulSave() {
        SpecimenDTO unit = new SpecimenDTO();
        unit.setFullIdentifier("S123");

        RowEditEvent<SpecimenDTO> event = mock(RowEditEvent.class);
        when(event.getObject()).thenReturn(unit);

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            // WHEN
            lazyModel.handleRowEdit(event);


            // THEN
            verify(specimenService).save(unit);

            messageUtilsMock.verify(() ->
                    MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.updated", "S123"));
        }
    }

    @Test
    void testHandleRowEdit_failedSave() {
        SpecimenDTO unit = new SpecimenDTO();
        unit.setFullIdentifier("S123");

        RowEditEvent<SpecimenDTO> event = mock(RowEditEvent.class);
        when(event.getObject()).thenReturn(unit);

        doThrow(new FailedRecordingUnitSaveException("")).when(specimenService).save(any());

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            // WHEN
            lazyModel.handleRowEdit(event);

            // THEN
            verify(specimenService).save(unit);

            messageUtilsMock.verify(() ->
                    MessageUtils.displayErrorMessage(langBean, "common.entity.recordingUnits.updateFailed", "S123"));
        }
    }

    @Test
    void testSaveFieldBulk_updatesTypeAndDisplaysMessage() {
        SpecimenDTO r1 = new SpecimenDTO();
        r1.setId(1L);
        SpecimenDTO r2 = new SpecimenDTO();
        r2.setId(2L);

        ConceptDTO newType = new ConceptDTO();
        lazyModel.setBulkEditTypeValue(newType);
        lazyModel.setSelectedUnits(List.of(r1, r2));

        when(specimenService.bulkUpdateType(anyList(), eq(newType))).thenReturn(2);

        try (MockedStatic<MessageUtils> messageUtilsMock = mockStatic(MessageUtils.class)) {
            lazyModel.saveFieldBulk();

            // Confirm both were updated
            assertSame(newType, r1.getType());
            assertSame(newType, r2.getType());

            verify(specimenService).bulkUpdateType(List.of(1L, 2L), newType);

            messageUtilsMock.verify(() ->
                    MessageUtils.displayInfoMessage(langBean, "common.entity.recordingUnits.bulkUpdated", 2));
        }


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
    void prepareFilterDTO_fullIdentifierFilterWithValue_isAdded() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(SpecimenSpec.FULL_IDENTIFIER_FILTER,
                FilterMeta.builder().field(SpecimenSpec.FULL_IDENTIFIER_FILTER).filterValue("sia-2025-1").build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.containsColumn(SpecimenSpec.FULL_IDENTIFIER_FILTER));
        assertEquals("sia-2025-1", filterDTO.valueOfAsString(SpecimenSpec.FULL_IDENTIFIER_FILTER));
        assertEquals(FilterDTO.FilterType.CONTAINS, filterDTO.filterOf(SpecimenSpec.FULL_IDENTIFIER_FILTER).getType());
    }

    @Test
    void prepareFilterDTO_fullIdentifierFilterWithNullValue_isSkipped() {
        FilterDTO filterDTO = new FilterDTO(false);
        Map<String, FilterMeta> filterBy = new HashMap<>();
        filterBy.put(SpecimenSpec.FULL_IDENTIFIER_FILTER,
                FilterMeta.builder().field(SpecimenSpec.FULL_IDENTIFIER_FILTER).build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertTrue(filterDTO.getColumns().isEmpty());
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
        filterBy.put(SpecimenSpec.FULL_IDENTIFIER_FILTER,
                FilterMeta.builder().field(SpecimenSpec.FULL_IDENTIFIER_FILTER).filterValue(42).build());

        lazyModel.prepareFilterDTO(filterBy, filterDTO);

        assertEquals("42", filterDTO.valueOfAsString(SpecimenSpec.FULL_IDENTIFIER_FILTER));
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
    void prepareSortDTO_fullIdentifierAscending_isAdded() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put(SpecimenSpec.FULL_IDENTIFIER_FILTER,
                SortMeta.builder().field(SpecimenSpec.FULL_IDENTIFIER_FILTER).order(SortOrder.ASCENDING).build());

        lazyModel.prepareSortDTO(sortBy, sortDTO);

        assertEquals(SortDTO.SortOrder.ASC, sortDTO.orderOf(SpecimenSpec.FULL_IDENTIFIER_FILTER));
    }

    @Test
    void prepareSortDTO_fullIdentifierDescending_isAdded() {
        SortDTO sortDTO = new SortDTO();
        Map<String, SortMeta> sortBy = new HashMap<>();
        sortBy.put(SpecimenSpec.FULL_IDENTIFIER_FILTER,
                SortMeta.builder().field(SpecimenSpec.FULL_IDENTIFIER_FILTER).order(SortOrder.DESCENDING).build());

        lazyModel.prepareSortDTO(sortBy, sortDTO);

        assertEquals(SortDTO.SortOrder.DESC, sortDTO.orderOf(SpecimenSpec.FULL_IDENTIFIER_FILTER));
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

}