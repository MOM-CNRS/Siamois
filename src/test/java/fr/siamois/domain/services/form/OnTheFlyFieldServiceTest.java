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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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

    @Test
    void fieldsOf_readsTheLinkSetOfEveryKindOfRecord() {
        var specimen = new fr.siamois.domain.models.specimen.Specimen();
        var phase = new fr.siamois.domain.models.phase.Phase();
        var container = new fr.siamois.domain.models.container.Container();
        var document = new fr.siamois.domain.models.document.Document();
        var study = new fr.siamois.domain.models.study.Study();
        specimen.getOnTheFlyFields().add(measurement(2L, null));
        phase.getOnTheFlyFields().add(measurement(1L, null));
        phase.getOnTheFlyFields().add(measurement(0L, null));
        container.getOnTheFlyFields().add(measurement(3L, null));
        document.getOnTheFlyFields().add(measurement(4L, null));
        study.getOnTheFlyFields().add(measurement(5L, null));
        lenient().when(entityManager.find(fr.siamois.domain.models.specimen.Specimen.class, 5L)).thenReturn(specimen);
        lenient().when(entityManager.find(fr.siamois.domain.models.phase.Phase.class, 5L)).thenReturn(phase);
        lenient().when(entityManager.find(fr.siamois.domain.models.container.Container.class, 5L)).thenReturn(container);
        lenient().when(entityManager.find(fr.siamois.domain.models.document.Document.class, 5L)).thenReturn(document);
        lenient().when(entityManager.find(fr.siamois.domain.models.study.Study.class, 5L)).thenReturn(study);

        assertEquals(List.of(2L), ids(service.fieldsOf(ConfigurableTable.MOBILIER, 5L)));
        assertEquals(List.of(0L, 1L), ids(service.fieldsOf(ConfigurableTable.PHASE, 5L)));
        assertEquals(List.of(3L), ids(service.fieldsOf(ConfigurableTable.CONTENANT, 5L)));
        assertEquals(List.of(4L), ids(service.fieldsOf(ConfigurableTable.DOCUMENT, 5L)));
        assertEquals(List.of(5L), ids(service.fieldsOf(ConfigurableTable.STUDY, 5L)));
        assertTrue(service.fieldsOf(ConfigurableTable.PROJECT, 5L).isEmpty());
        assertTrue(service.fieldsOf(ConfigurableTable.UE, null).isEmpty());
        assertTrue(service.fieldsOf(ConfigurableTable.UE, 404L).isEmpty());
    }

    @Test
    void add_andRemove_needTheRecordToExist() {
        CustomFieldMeasurement field = measurement(1L, null);

        assertThrows(IllegalArgumentException.class, () -> service.add(ConfigurableTable.UE, 404L, "p", field));
        assertThrows(IllegalArgumentException.class, () -> service.remove(ConfigurableTable.UE, 404L, 1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findReusableMeasurements_keepsOneMeasurePerNameAndUnit() {
        var query = mock(jakarta.persistence.TypedQuery.class);
        when(entityManager.createQuery(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(
                fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldOnTheFly.class))).thenReturn(query);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        var metre = fr.siamois.domain.models.form.measurement.UnitDefinition.builder().symbol("m").build();
        CustomFieldMeasurement first = measurement(1L, null);
        first.setLabel("Hauteur");
        first.setUnit(metre);
        CustomFieldMeasurement again = measurement(2L, null);
        again.setLabel("Hauteur");
        again.setUnit(metre);
        CustomFieldMeasurement other = measurement(3L, null);
        other.setLabel("Largeur");
        when(query.getResultList()).thenReturn(List.of(first, again, other));

        var found = service.findReusableMeasurements(ConfigurableTable.UE, 9L, " Haut ", 10);

        assertEquals(List.of(1L, 3L), ids(found));
        assertEquals(1, service.findReusableMeasurements(ConfigurableTable.UE, 9L, null, 1).size());
        assertTrue(service.findReusableMeasurements(ConfigurableTable.UE, null, "h", 10).isEmpty());
        assertTrue(service.findReusableMeasurements(ConfigurableTable.PROJECT, 9L, "h", 10).isEmpty());
    }

    private static List<Long> ids(List<? extends fr.siamois.domain.models.form.customfield.CustomField> fields) {
        return fields.stream().map(f -> f.getId()).toList();
    }
}
