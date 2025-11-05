package fr.siamois.infrastructure.database.initializer.seeder;


import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customform.*;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldAnswerDTO;
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

    private CustomFieldSeederSpec fieldSpec(ConceptSeeder.ConceptKey key) {
        return new CustomFieldSeederSpec(
                CustomFieldText.class,
                true,
                "",              // valueBinding
                key,            // conceptKey (unused by this seeder)
                "type",          // binding/type key
                "", "", ""
        );
    }

    private CustomColDTO colDTO(CustomFieldSeederSpec spec) {
        return colDTO(spec, null);
    }

    private CustomColDTO colDTO(CustomFieldSeederSpec spec, EnabledWhenSpecSeedDTO enabled) {
        return new CustomColDTO(
                /* readOnly   */ true,
                /* isRequired */ true,
                /* field      */ spec,
                /* className  */ "col-6",
                /* enabledWhen*/ enabled
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

    @Test
    void seed_mapsEnabledWhenSpec_intoModel() {

        // Arrange
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setExternalVocabularyId("extid");
        vocabulary.setBaseUri("baseUri");
        Concept c = new Concept();
        c.setExternalId("1");
        c.setVocabulary(vocabulary);
        Concept c2 = new Concept();
        c2.setExternalId("2");
        c2.setVocabulary(vocabulary);

        // Champ de la colonne (affiché)
        CustomField columnField = new CustomFieldText();
        columnField.setId(100L);
        columnField.setLabel("Column 1");
        columnField.setConcept(c);

        // Champ observé par la règle (comparé)
        CustomField comparedField = new CustomFieldText();
        comparedField.setId(200L);
        columnField.setLabel("Column 2");
        columnField.setConcept(c2);

        // Specs pour retrouver/creer ces champs
        CustomFieldSeederSpec colSpec = fieldSpec(new ConceptSeeder.ConceptKey("extid","1"));        // helper existant
        CustomFieldSeederSpec condSpec = fieldSpec(new ConceptSeeder.ConceptKey("extid","2"));

        // DTO "valeur attendue" = réponse de type Concept
        var conceptKey = new fr.siamois.infrastructure.database.initializer.seeder.ConceptSeeder.ConceptKey("extid", "2");
        var expectedValue = new CustomFieldAnswerDTO(
                fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode.class,
                condSpec, // IMPORTANT: même champ que la condition
                conceptKey
        );

        // Règle enabledWhen = EQUALS (une seule valeur)
        EnabledWhenSpecSeedDTO enabled = new EnabledWhenSpecSeedDTO(
                EnabledWhenSpecSeedDTO.Operator.EQUALS,
                condSpec,                         // champ observé
                java.util.List.of(expectedValue)  // 1 valeur
        );

        // Colonne avec enabledWhen
        CustomColDTO col = colDTO(colSpec, enabled);
        CustomRowDTO row = rowDTO(col);
        CustomFormPanelDTO panel = panelDTO(row);
        CustomFormDTO dto = formDTO(java.util.List.of(panel));

        when(customFormRepository.findByNameAndDescription("My Form", "A sample form"))
                .thenReturn(Optional.empty());

        // fieldSeeder doit renvoyer le bon champ selon la spec passée
        when(fieldSeeder.findFieldOrThrow(colSpec)).thenReturn(columnField);
        when(fieldSeeder.findFieldOrThrow(condSpec)).thenReturn(comparedField);

        ArgumentCaptor<CustomForm> formCaptor = ArgumentCaptor.forClass(CustomForm.class);

        // Act
        seeder.seed(java.util.List.of(dto));

        // Assert
        verify(customFormRepository).save(formCaptor.capture());
        CustomForm saved = formCaptor.getValue();

        // retrouve la 1ère colonne
        CustomCol savedCol = saved.getLayout().get(0).getRows().get(0).getColumns().get(0);

        assertSame(columnField, savedCol.getField());
        assertNotNull(savedCol.getEnabledWhenSpec(), "enabledWhen should be set");

        EnabledWhenJson ew = savedCol.getEnabledWhenSpec();
        assertEquals(EnabledWhenJson.Op.eq, ew.getOp());
        assertEquals(200L, ew.getFieldId()); // id du champ comparé
        assertNotNull(ew.getValues());
        assertEquals(1, ew.getValues().size());

        EnabledWhenJson.ValueJson vj = ew.getValues().get(0);
        assertEquals(
                fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode.class.getName(),
                vj.getAnswerClass()
        );
        assertNotNull(vj.getValue());
        assertEquals("extid", vj.getValue().get("vocabularyExtId").asText());
        assertEquals("2",  vj.getValue().get("conceptExtId").asText());

        // vérifie que les deux champs ont été résolus via le seeder
        verify(fieldSeeder).findFieldOrThrow(colSpec);
        verify(fieldSeeder).findFieldOrThrow(condSpec);
    }

    // Build a form DTO with a single panel/row/col
    private CustomFormDTO formWith(CustomColDTO col) {
        return formDTO(List.of(panelDTO(rowDTO(col))));
    }

    @Test
    void seed_fails_whenEnabledWhen_field_isNull() {
        // Arrange
        CustomField columnField = new CustomFieldText(); columnField.setId(100L);
        CustomFieldSeederSpec colSpec = fieldSpec(new ConceptSeeder.ConceptKey("ext", "CF"));
        CustomColDTO col = colDTO(colSpec,
                new EnabledWhenSpecSeedDTO(
                        EnabledWhenSpecSeedDTO.Operator.EQUALS,
                        null,                  // <-- field is null
                        List.of()              // irrelevant, we should fail before
                )
        );
        CustomFormDTO dto = formWith(col);

        when(customFormRepository.findByNameAndDescription("My Form","A sample form"))
                .thenReturn(Optional.empty());

        // Act + Assert
        List<CustomFormDTO> list = List.of(dto);
        assertThrows(IllegalArgumentException.class, () -> seeder.seed(list));
        verify(customFormRepository, never()).save(any());
    }

    @Test
    void seed_fails_whenEnabledWhen_values_nullOrEmpty() {
        CustomField columnField = new CustomFieldText(); columnField.setId(100L);
        CustomField observedField = new CustomFieldText(); observedField.setId(200L);

        CustomFieldSeederSpec colSpec  = fieldSpec(new ConceptSeeder.ConceptKey("ext","CF"));
        CustomFieldSeederSpec condSpec = fieldSpec(new ConceptSeeder.ConceptKey("ext","COND"));

        when(customFormRepository.findByNameAndDescription("My Form","A sample form"))
                .thenReturn(Optional.empty());

        // null values
        CustomColDTO colNull = colDTO(colSpec,
                new EnabledWhenSpecSeedDTO(EnabledWhenSpecSeedDTO.Operator.IN, condSpec, null));
        List<CustomFormDTO> list = List.of(formWith(colNull));
        assertThrows(IllegalArgumentException.class, () -> seeder.seed(list));

        // empty values
        CustomColDTO colEmpty = colDTO(colSpec,
                new EnabledWhenSpecSeedDTO(EnabledWhenSpecSeedDTO.Operator.IN, condSpec, List.of()));

        List<CustomFormDTO> list2 = List.of(formWith(colEmpty));
        assertThrows(IllegalArgumentException.class, () -> seeder.seed(list2));

        verify(customFormRepository, never()).save(any());
    }

    // One expected value of type Concept bound to a given observed-field spec
    private CustomFieldAnswerDTO expectedConceptValue(CustomFieldSeederSpec observedSpec,
                                                      String vocabExtId, String conceptExtId) {
        return new CustomFieldAnswerDTO(
                fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectOneFromFieldCode.class,
                observedSpec,
                new ConceptSeeder.ConceptKey(vocabExtId, conceptExtId)
        );
    }

    @Test
    void seed_fails_whenEquals_hasMoreThanOneValue() {
        CustomField columnField = new CustomFieldText(); columnField.setId(100L);
        CustomField observedField = new CustomFieldText(); observedField.setId(200L);

        CustomFieldSeederSpec colSpec  = fieldSpec(new ConceptSeeder.ConceptKey("ext","CF"));
        CustomFieldSeederSpec condSpec = fieldSpec(new ConceptSeeder.ConceptKey("ext","COND"));

        var v1 = expectedConceptValue(condSpec, "ext", "C1");
        var v2 = expectedConceptValue(condSpec, "ext", "C2");

        CustomColDTO col = colDTO(colSpec,
                new EnabledWhenSpecSeedDTO(EnabledWhenSpecSeedDTO.Operator.EQUALS, condSpec, List.of(v1, v2)));

        when(customFormRepository.findByNameAndDescription("My Form","A sample form"))
                .thenReturn(Optional.empty());
        when(fieldSeeder.findFieldOrThrow(condSpec)).thenReturn(observedField);

        List<CustomFormDTO> list = List.of(formWith(col));
        assertThrows(IllegalArgumentException.class, () -> seeder.seed(list));
        verify(customFormRepository, never()).save(any());
    }

    @Test
    void seed_fails_whenNotEquals_hasMoreThanOneValue() {
        CustomField columnField = new CustomFieldText(); columnField.setId(100L);
        CustomField observedField = new CustomFieldText(); observedField.setId(200L);

        CustomFieldSeederSpec colSpec  = fieldSpec(new ConceptSeeder.ConceptKey("ext","CF"));
        CustomFieldSeederSpec condSpec = fieldSpec(new ConceptSeeder.ConceptKey("ext","COND"));

        var v1 = expectedConceptValue(condSpec, "ext", "C1");
        var v2 = expectedConceptValue(condSpec, "ext", "C2");

        CustomColDTO col = colDTO(colSpec,
                new EnabledWhenSpecSeedDTO(EnabledWhenSpecSeedDTO.Operator.NOT_EQUALS, condSpec, List.of(v1, v2)));

        when(customFormRepository.findByNameAndDescription("My Form","A sample form"))
                .thenReturn(Optional.empty());
        when(fieldSeeder.findFieldOrThrow(condSpec)).thenReturn(observedField);

        List<CustomFormDTO> list = List.of(formWith(col));
        assertThrows(IllegalArgumentException.class, () -> seeder.seed(list));
        verify(customFormRepository, never()).save(any());
    }

    @Test
    void seed_fails_whenIn_hasNoValues() {
        CustomField columnField = new CustomFieldText(); columnField.setId(100L);
        CustomField observedField = new CustomFieldText(); observedField.setId(200L);

        CustomFieldSeederSpec colSpec  = fieldSpec(new ConceptSeeder.ConceptKey("ext","CF"));
        CustomFieldSeederSpec condSpec = fieldSpec(new ConceptSeeder.ConceptKey("ext","COND"));

        CustomColDTO col = colDTO(colSpec,
                new EnabledWhenSpecSeedDTO(EnabledWhenSpecSeedDTO.Operator.IN, condSpec, List.of()));

        when(customFormRepository.findByNameAndDescription("My Form","A sample form"))
                .thenReturn(Optional.empty());

        List<CustomFormDTO> list = List.of(formWith(col));
        assertThrows(IllegalArgumentException.class, () -> seeder.seed(list));
        verify(customFormRepository, never()).save(any());
    }

    @Test
    void seed_fails_whenExpectedValues_fieldDiffersFromEnabledField() {
        CustomField columnField = new CustomFieldText(); columnField.setId(100L);
        CustomField observedField = new CustomFieldText(); observedField.setId(200L);
        CustomField wrongObserved = new CustomFieldText(); wrongObserved.setId(300L);

        CustomFieldSeederSpec colSpec   = fieldSpec(new ConceptSeeder.ConceptKey("ext","CF"));
        CustomFieldSeederSpec condSpec  = fieldSpec(new ConceptSeeder.ConceptKey("ext","COND"));
        CustomFieldSeederSpec wrongSpec = fieldSpec(new ConceptSeeder.ConceptKey("ext","WRONG"));

        var v1 = expectedConceptValue(wrongSpec, "ext", "C1"); // <-- wrong field spec

        CustomColDTO col = colDTO(colSpec,
                new EnabledWhenSpecSeedDTO(EnabledWhenSpecSeedDTO.Operator.EQUALS, condSpec, List.of(v1)));

        when(customFormRepository.findByNameAndDescription("My Form","A sample form"))
                .thenReturn(Optional.empty());
        when(fieldSeeder.findFieldOrThrow(condSpec)).thenReturn(observedField);
        List<CustomFormDTO> list = List.of(formWith(col));
        assertThrows(IllegalArgumentException.class, () -> seeder.seed(list));
        verify(customFormRepository, never()).save(any());
    }






}
