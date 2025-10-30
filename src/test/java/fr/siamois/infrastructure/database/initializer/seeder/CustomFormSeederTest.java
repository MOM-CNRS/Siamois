package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;
import fr.siamois.infrastructure.database.initializer.seeder.customform.*;
import fr.siamois.infrastructure.database.repositories.form.CustomFormRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFormSeederTest {

    @Mock
    CustomFormRepository customFormRepository;

    @Mock
    CustomFieldSeeder fieldSeeder;

    @InjectMocks
    CustomFormSeeder seeder;

    // --- Helpers to build a minimal valid DTO graph ---

    private CustomFieldSeederSpec fieldSpec() {
        return new CustomFieldSeederSpec(
                CustomFieldText.class,
                true,
                "",              // valueBinding
                null,            // conceptKey (unused by this seeder)
                "type",          // binding/type key
                "", "", ""
        );
    }

    private CustomColDTO colDTO(CustomFieldSeederSpec spec) {
        return new CustomColDTO(
                /* readOnly */ true,
                /* isRequired */ true,
                /* field */ spec,
                /* className */ "col-6"

        );
    }

    private CustomRowDTO rowDTO(CustomColDTO... cols) {
        return new CustomRowDTO(List.of(cols));
    }

    private CustomFormPanelDTO panelDTO(CustomRowDTO... rows) {
        return new CustomFormPanelDTO(
                /* className */ "panel-class",
                /* name */ "Main Panel",
                /* rows */ List.of(rows),
                /* isSystemPanel */ true

        );
    }

    private CustomFormDTO formDTO(List<CustomFormPanelDTO> panels) {
        return new CustomFormDTO(
                /* description */ "A sample form",
                /* name */ "My Form",
                /* layout */ panels
        );
    }

    // --- Tests ---

    @Test
    void seed_createsForm_whenNotExists_andResolvesFields()  {
        // Arrange
        CustomField field = new CustomFieldText();
        CustomFieldSeederSpec spec = fieldSpec();
        CustomColDTO col = colDTO(spec);
        CustomRowDTO row = rowDTO(col);
        CustomFormPanelDTO panel = panelDTO(row);
        CustomFormDTO dto = formDTO(List.of(panel));

        when(customFormRepository.findByNameAndDescription("My Form", "A sample form"))
                .thenReturn(Optional.empty());
        when(fieldSeeder.findFieldOrThrow(spec)).thenReturn(field);

        ArgumentCaptor<CustomForm> formCaptor = ArgumentCaptor.forClass(CustomForm.class);

        // Act
        seeder.seed(List.of(dto));

        // Assert
        verify(customFormRepository).save(formCaptor.capture());
        CustomForm saved = formCaptor.getValue();

        assertEquals("My Form", saved.getName());
        assertEquals("A sample form", saved.getDescription());

        // Validate layout mapping
        List<CustomFormPanel> panels = saved.getLayout();
        assertNotNull(panels);
        assertEquals(1, panels.size());
        CustomFormPanel savedPanel = panels.get(0);
        assertTrue(savedPanel.getIsSystemPanel());
        assertEquals("Main Panel", savedPanel.getName());
        assertEquals("panel-class", savedPanel.getClassName());

        List<CustomRow> rows = savedPanel.getRows();
        assertNotNull(rows);
        assertEquals(1, rows.size());

        List<CustomCol> cols = rows.get(0).getColumns();
        assertNotNull(cols);
        assertEquals(1, cols.size());

        CustomCol savedCol = cols.get(0);
        assertTrue(savedCol.isReadOnly());
        assertTrue(savedCol.isRequired());
        assertEquals("col-6", savedCol.getClassName());
        assertSame(field, savedCol.getField()); // must be the resolved field

        verify(fieldSeeder).findFieldOrThrow(spec);
    }

    @Test
    void seed_update_whenFormAlreadyExists()  {
        // Arrange
        CustomForm existing = new CustomForm();
        existing.setName("My Form");
        existing.setDescription("A sample form");

        CustomFormDTO dto = formDTO(List.of(panelDTO(rowDTO(colDTO(fieldSpec())))));

        when(customFormRepository.findByNameAndDescription("My Form", "A sample form"))
                .thenReturn(Optional.of(existing));

        // Act
        seeder.seed(List.of(dto));

        // Assert
        verify(customFormRepository, times(1)).save(any());


    }

    @Test
    void findOrNull_returnsNullWhenMissing_andReturnsFormWhenFound() {
        CustomFormDTO dto = formDTO(List.of());
        when(customFormRepository.findByNameAndDescription("My Form", "A sample form"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new CustomForm()));

        assertNull(seeder.findOrNull(dto));
        assertNotNull(seeder.findOrNull(dto));
    }

    @Test
    void findOrThrow_returnsFormWhenFound_elseThrows() {
        CustomFormDTO dto = formDTO(List.of());
        CustomForm form = new CustomForm();
        form.setName("My Form");
        form.setDescription("A sample form");

        when(customFormRepository.findByNameAndDescription("My Form", "A sample form"))
                .thenReturn(Optional.of(form));
        assertSame(form, seeder.findOrThrow(dto));

        when(customFormRepository.findByNameAndDescription("My Form", "A sample form"))
                .thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> seeder.findOrThrow(dto));
    }
}
