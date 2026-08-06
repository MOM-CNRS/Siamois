package fr.siamois.infrastructure.database.initializer;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemFieldInitializerTest {

    private static final String IDENTIFIER_FIELD = "common.label.identifier";
    private static final String CATEGORY_FIELD = "specimen.field.category";

    @Mock
    private CustomFieldRepository customFieldRepository;

    @InjectMocks
    private SystemFieldInitializer initializer;

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
        assertThat(category.getValueBinding()).isEqualTo("category");
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
}
