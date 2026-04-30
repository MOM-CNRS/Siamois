package fr.siamois.infrastructure.database.initializer.seeder;

import fr.siamois.domain.models.exceptions.database.DatabaseDataInitException;
import fr.siamois.domain.models.form.customfield.*;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.VocabularyDTO;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeeder;
import fr.siamois.infrastructure.database.initializer.seeder.customfield.CustomFieldSeederSpec;
import fr.siamois.infrastructure.database.repositories.form.CustomFieldRepository;
import fr.siamois.mapper.UnitDefinitionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomFieldSeederTest {

    @Mock
    CustomFieldRepository customFieldRepository;
    @Mock
    ConceptSeeder conceptSeeder;
    @Mock
    UnitDefinitionSeeder unitDefinitionSeeder;
    @Mock
    UnitDefinitionMapper mapper;

    @InjectMocks
    CustomFieldSeeder seeder;

    @Test
    void seed_CreateMeasurementField() throws DatabaseDataInitException {
        // 1. Prepare Data
        Concept mainConcept = new Concept();
        Concept unitConcept = new Concept();
        ConceptSeeder.ConceptKey key = new ConceptSeeder.ConceptKey("th230", "123456");

        // Prepare DTO hierarchy for the Spec
        VocabularyDTO vocabDTO = new VocabularyDTO();
        vocabDTO.setExternalVocabularyId("VOC-01");

        ConceptDTO conceptDTO = new ConceptDTO();
        conceptDTO.setExternalId("UNIT-01");
        conceptDTO.setVocabulary(vocabDTO);

        UnitDefinitionDTO unitDTO = new UnitDefinitionDTO();
        unitDTO.setConcept(conceptDTO);

        CustomFieldSeederSpec spec = new CustomFieldSeederSpec(
                CustomFieldMeasurement.class,
                true,
                "label",
                key,
                "binding",
                null, null, null, // fieldCode, icon, style
                false,            // isTextArea
                unitDTO           // <--- This triggers the measurement block
        );

        UnitDefinition unitDefinition = new UnitDefinition();
        unitDefinition.setConcept(new Concept()); // Will be overwritten in code

        // 2. Mocking
        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(mainConcept);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(any(), anyBoolean(), any(), any()))
                .thenReturn(Optional.empty());

        // Mock the mapper and the secondary concept lookup
        when(mapper.invertConvert(unitDTO)).thenReturn(unitDefinition);
        when(conceptSeeder.findConceptOrReturnNull("VOC-01", "UNIT-01")).thenReturn(unitConcept);
        when(unitDefinitionSeeder.findUnitOrReturnNull(unitConcept)).thenReturn(new UnitDefinition());

        // 3. Execute
        seeder.seed(List.of(spec));

        // 4. Verify
        verify(mapper).invertConvert(unitDTO);
        verify(unitDefinitionSeeder).findUnitOrReturnNull(unitConcept);
        verify(customFieldRepository).save(any(CustomFieldMeasurement.class));
    }

    // --- Existing tests remain the same ---

    @Test
    void seed_AlreadyExists() throws DatabaseDataInitException {
        Concept c = new Concept();
        ConceptSeeder.ConceptKey key =  new ConceptSeeder.ConceptKey("th230", "123456");
        List<CustomFieldSeederSpec> toInsert = List.of(
                new CustomFieldSeederSpec(CustomFieldText.class, true, "", key, "type", "", "", "")
        );

        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(c);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(CustomFieldText.class, true, "type", c))
                .thenReturn(Optional.of(new CustomFieldText()));

        seeder.seed(toInsert);
        verify(customFieldRepository, never()).save(any(CustomField.class));
    }

    @Test
    void seed_throw_ifConceptNotFound()  {
        ConceptSeeder.ConceptKey key =  new ConceptSeeder.ConceptKey("th230", "123456");
        List<CustomFieldSeederSpec> toInsert = List.of(
                new CustomFieldSeederSpec(CustomFieldText.class, true, "", key, "type", "", "", "")
        );

        when(conceptSeeder.findConceptOrThrow(key)).thenThrow(new IllegalStateException());
        assertThrows(IllegalStateException.class, () -> seeder.seed(toInsert));
    }

    @Test
    void seed_Create() throws DatabaseDataInitException {
        Concept c = new Concept();
        ConceptSeeder.ConceptKey key =  new ConceptSeeder.ConceptKey("th230", "123456");
        List<CustomFieldSeederSpec> toInsert = List.of(
                new CustomFieldSeederSpec(CustomFieldSelectOneFromFieldCode.class, true, "", key, "type", "", "", "")
        );

        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(c);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(CustomFieldSelectOneFromFieldCode.class, true, "type", c))
                .thenReturn(Optional.empty());

        seeder.seed(toInsert);
        verify(customFieldRepository, times(1)).save(any(CustomField.class));
    }

    @Test
    void findFieldOrThrow_returnsField() {
        Concept c = new Concept();
        ConceptSeeder.ConceptKey key = new ConceptSeeder.ConceptKey("th230", "123456");
        CustomFieldSeederSpec spec = new CustomFieldSeederSpec(CustomFieldText.class, true, "", key, "type", "", "", "");
        CustomFieldText expected = new CustomFieldText();

        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(c);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(CustomFieldText.class, true, "type", c))
                .thenReturn(Optional.of(expected));

        CustomField result = seeder.findFieldOrThrow(spec);
        assertSame(expected, result);
    }

    @Test
    void findFieldOrThrow_throwsWhenFieldMissing() {
        Concept c = new Concept();
        ConceptSeeder.ConceptKey key = new ConceptSeeder.ConceptKey("th230", "123456");
        CustomFieldSeederSpec spec = new CustomFieldSeederSpec(CustomFieldText.class, true, "", key, "type", "", "", "");

        when(conceptSeeder.findConceptOrThrow(key)).thenReturn(c);
        when(customFieldRepository.findByTypeAndSystemAndBindingAndConcept(CustomFieldText.class, true, "type", c))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> seeder.findFieldOrThrow(spec));
    }
}