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

    private static final String IDENTIFIER_FIELD = "recordingunit.field.identifier";
    private static final String CATEGORY_FIELD = "specimen.field.category";

    @Mock
    private CustomFieldRepository customFieldRepository;

    @InjectMocks
    private SystemFieldInitializer initializer;

    private long definedSystemFieldCount() {
        return Arrays.stream(ConfigurableTable.values())
                .flatMap(table -> SystemFieldCatalog.fieldsOf(table).stream())
                .map(SystemFieldCatalog::identityOf)
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
     * The definitions carry ids of their own, local to the class they are declared in, and concepts
     * that were never persisted. Neither can reach the row: the id is the database's to hand out,
     * and a transient concept would fail the insert.
     */
    @Test
    void initialize_shouldSaveRowsWithNeitherTheDefinitionsIdNorItsTransientAssociations() throws DatabaseDataInitException {
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of());
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        ArgumentCaptor<CustomField> saved = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(field -> {
            assertThat(field.getId()).isNull();
            assertThat(field.getConcept()).isNull();
            assertThat(field.getAuthor()).isNull();
        });
    }

    @Test
    void initialize_shouldLeaveTheFieldsThatAlreadyHaveARowAlone() throws DatabaseDataInitException {
        CustomField existing = CustomFieldText.builder()
                .id(1L).label(IDENTIFIER_FIELD).valueBinding("fullIdentifier").isSystemField(true).build();
        when(customFieldRepository.findAllSystemFields()).thenReturn(List.of(existing));
        when(customFieldRepository.save(any(CustomField.class))).thenAnswer(call -> call.getArgument(0));

        initializer.initialize();

        ArgumentCaptor<CustomField> saved = ArgumentCaptor.forClass(CustomField.class);
        verify(customFieldRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(CustomField::getLabel).doesNotContain(IDENTIFIER_FIELD);
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
