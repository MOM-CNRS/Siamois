package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.recordingunit.CustomFieldMeasurement;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.services.measurement.UnitDefinitionService;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemFieldInitializerTest {

    private static final String IDENTIFIER_FIELD = "common.label.identifier";
    private static final String CATEGORY_FIELD = "specimen.field.type";
    private static final long Z_INF_ID = -322L;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @Mock
    private UnitDefinitionService unitDefinitionService;

    @InjectMocks
    private SystemFieldInitializer initializer;

    /** The catalogue as seeded: the managed unit of the symbol and dimension a definition declares. */
    @BeforeEach
    void seededCatalogue() {
        lenient().when(unitDefinitionService.resolve(any())).thenAnswer(call -> {
            UnitDefinition declared = call.getArgument(0);
            return new UnitDefinition(1L, null, declared.getLabel(), declared.getSymbol(),
                    declared.getDimension(), declared.getFactorToBase(), declared.isSystemBase());
        });
    }

    private static CustomFieldMeasurement measurementRow(long id, UnitDefinition unit) {
        CustomFieldMeasurement row = CustomFieldMeasurement.builder().id(id).isSystemField(true).build();
        row.setUnit(unit);
        return row;
    }

    private long definedSystemFieldCount() {
        return Arrays.stream(ConfigurableTable.values())
                .flatMap(table -> SystemFieldCatalog.fieldsOf(table).stream())
                .map(CustomField::getId)
                .distinct()
                .count();
    }

    @Test
    void initialize_shouldCreateARowForEverySystemFieldTheApplicationDefines() throws DatabaseDataInitException {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of());
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        verify(customFieldRepository, times((int) definedSystemFieldCount())).save(any(CustomField.class));
    }

    @Test
    void initialize_shouldKeepTheSubclassAndItsOwnColumns() throws DatabaseDataInitException {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of());
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        ArgumentCaptor<CustomField> saved = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository, atLeastOnce()).save(saved.capture());
        CustomField category = saved.getAllValues().stream()
                .filter(field -> CATEGORY_FIELD.equals(field.getLabel()))
                .findFirst()
                .orElseThrow();
        assertThat(category).isInstanceOf(CustomFieldSelectOneFromFieldCode.class);
        assertThat(((CustomFieldSelectOneFromFieldCode) category).getFieldCode()).isNotBlank();
        assertThat(category.getIsSystemField()).isTrue();
        assertThat(category.getValueBinding()).isEqualTo("type");
    }

    /**
     * The id is kept — {@code AssignedOrSequenceIdGenerator} persists it as-is rather than drawing
     * one from the sequence — but concepts and authors are transient/meaningless for a system field
     * and would fail the insert if copied.
     */
    @Test
    void initialize_shouldSaveRowsWithTheDefinitionsIdButNotItsTransientAssociations() throws DatabaseDataInitException {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of());
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        ArgumentCaptor<CustomField> saved = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(field -> {
            assertThat(field.getId()).isNotNull();
            assertThat(field.getConcept()).isNull();
            assertThat(field.getAuthor()).isNull();
        });
    }

    @Test
    void initialize_shouldLeaveTheFieldsThatAlreadyHaveARowAlone() throws DatabaseDataInitException {
        Long identifierFieldId = SystemFieldCatalog.fieldsOf(ConfigurableTable.UE).stream()
                .filter(field -> IDENTIFIER_FIELD.equals(field.getLabel()))
                .findFirst().orElseThrow().getId();
        CustomField existing = CustomFieldText.builder()
                .id(identifierFieldId).label(IDENTIFIER_FIELD).valueBinding("fullIdentifier").isSystemField(true).build();
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(existing));
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        ArgumentCaptor<CustomField> saved = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CustomField::getId).doesNotContain(identifierFieldId);
    }

    @Test
    void initialize_shouldCreateNothingOnASecondRun() throws DatabaseDataInitException {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of());
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));
        initializer.initialize();
        List<CustomField> created = mockingDetails(customFieldRepository).getInvocations().stream()
                .filter(invocation -> "save".equals(invocation.getMethod().getName()))
                .map(invocation -> (CustomField) invocation.getArgument(0))
                .toList();
        reset(customFieldRepository);
        when(customFieldRepository.findAllSystemFields()).thenReturn(created);

        initializer.initialize();

        verify(customFieldRepository, never()).save(any(CustomField.class));
    }

    @Test
    void initialize_shouldGiveANewMeasurementRowTheCatalogueUnitItsDefinitionDeclares() throws DatabaseDataInitException {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of());
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        ArgumentCaptor<CustomField> saved = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository, atLeastOnce()).save(saved.capture());
        CustomField zInf = saved.getAllValues().stream()
                .filter(field -> field.getId() == Z_INF_ID).findFirst().orElseThrow();
        UnitDefinition unit = ((CustomFieldMeasurement) zInf).getUnit();
        assertThat(unit.getId()).isNotNull();
        assertThat(unit.getSymbol()).isEqualTo("m");
        assertThat(unit.getDimension()).isEqualTo(UnitDefinition.Dimension.LENGTH);
    }

    @Test
    void initialize_shouldGiveAnExistingUnitlessMeasurementRowItsDefaultUnit() throws DatabaseDataInitException {
        CustomFieldMeasurement zInf = measurementRow(Z_INF_ID, null);
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(zInf));
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        assertThat(zInf.getUnit()).isNotNull();
        assertThat(zInf.getUnit().getSymbol()).isEqualTo("m");
        verify(customFieldRepository).save(zInf);
    }

    @Test
    void initialize_shouldKeepAUnitAlreadyChosen() throws DatabaseDataInitException {
        UnitDefinition centimetre = new UnitDefinition(2L, null, "Centimètre", "cm", UnitDefinition.Dimension.LENGTH, 0.01, false);
        CustomFieldMeasurement zInf = measurementRow(Z_INF_ID, centimetre);
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(zInf));
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        assertThat(zInf.getUnit()).isSameAs(centimetre);
        verify(customFieldRepository, never()).save(zInf);
    }

    @Test
    void initialize_shouldFailWhenADeclaredUnitIsNotSeeded() {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(measurementRow(Z_INF_ID, null)));
        doThrow(new IllegalStateException("no unit")).when(unitDefinitionService).resolve(any());

        assertThatThrownBy(() -> initializer.initialize()).isInstanceOf(DatabaseDataInitException.class);
    }
}
