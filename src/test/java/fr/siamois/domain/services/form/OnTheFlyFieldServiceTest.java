package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.ui.form.dto.CustomColUiDto;
import fr.siamois.ui.form.dto.CustomFormPanelUiDto;
import fr.siamois.ui.form.dto.CustomRowUiDto;
import fr.siamois.ui.form.dto.FormUiDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnTheFlyFieldServiceTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private OnTheFlyFieldService service;

    private RecordingUnit unit;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        unit = new RecordingUnit();
        org.mockito.Mockito.lenient().when(entityManager.find(RecordingUnit.class, 5L)).thenReturn(unit);
    }

    private static CustomFormPanelUiDto panel(String name) {
        CustomFormPanelUiDto panel = new CustomFormPanelUiDto();
        panel.setName(name);
        panel.setRows(new java.util.ArrayList<>(java.util.List.of(new CustomRowUiDto())));
        return panel;
    }

    private static CustomFieldMeasurement measurement(long id, String panel) {
        CustomFieldMeasurement field = CustomFieldMeasurement.builder().build();
        field.setId(id);
        field.setPanelName(panel);
        return field;
    }

    @Test
    void add_linksTheFieldToTheRecordWithItsPanel() {
        CustomFieldMeasurement field = measurement(1L, null);

        service.add(ConfigurableTable.UE, 5L, "general", field);

        assertEquals("general", field.getPanelName());
        assertTrue(unit.getOnTheFlyFields().contains(field));
    }

    @Test
    void remove_unlinksTheFieldAndSaysWhetherItWasThere() {
        unit.getOnTheFlyFields().add(measurement(1L, "general"));

        assertTrue(service.remove(ConfigurableTable.UE, 5L, 1L));
        assertFalse(service.remove(ConfigurableTable.UE, 5L, 1L));
    }

    @Test
    void withOnTheFlyFields_addsTheFieldToItsPanelAndFallsBackToTheLastOne() {
        unit.getOnTheFlyFields().add(measurement(1L, "a"));
        unit.getOnTheFlyFields().add(measurement(2L, "gone"));
        FormUiDto base = new FormUiDto.Builder().addPanels(panel("a"), panel("b")).build();

        FormUiDto result = service.withOnTheFlyFields(base, ConfigurableTable.UE, 5L);

        assertEquals(2, result.getLayout().get(0).getRows().size());
        assertEquals(2, result.getLayout().get(1).getRows().size());
        CustomColUiDto col = result.getLayout().get(0).getRows().get(1).getColumns().get(0);
        assertEquals(1L, col.getField().getId());
        assertTrue(col.isCanBeRemoved());
        // the shared base form is left alone
        assertEquals(1, base.getLayout().get(0).getRows().size());
    }

    @Test
    void withOnTheFlyFields_returnsTheFormAsItIsWhenTheRecordHasNone() {
        FormUiDto base = new FormUiDto.Builder().addPanels(panel("a")).build();

        assertSame(base, service.withOnTheFlyFields(base, ConfigurableTable.UE, 5L));
    }

    @Test
    void copyOf_keepsTheDefinitionButNotTheIdentity() {
        CustomFieldMeasurement source = measurement(1L, "a");
        source.setLabel("Hauteur");

        CustomFieldMeasurement copy = service.copyOf(source);

        assertNull(copy.getId());
        assertNull(copy.getPanelName());
        assertEquals("Hauteur", copy.getLabel());
        assertFalse(copy.getIsSystemField());
    }
}
