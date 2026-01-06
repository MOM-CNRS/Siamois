package fr.siamois.domain.services.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldDateTime;
import fr.siamois.domain.models.form.customfield.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.form.customfield.CustomFieldText;
import fr.siamois.domain.models.form.customfieldanswer.*;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.form.FieldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FormServiceTest {

    @Mock
    private FormRepository formRepository;

    @Mock
    private LabelBean labelBean;

    @InjectMocks
    private FormService formService;

    private Concept recordingUnitType;
    private Institution institution;

    void setUpForReturnTypeSpecificTests() {
        recordingUnitType = mock(Concept.class);
        institution = mock(Institution.class);

        // Use deterministic IDs in stubs
        given(recordingUnitType.getId()).willReturn(101L);
        given(institution.getId()).willReturn(55L);
    }

    @Test
    void findAllFieldsBySpatialUnitId_success() {
        when(formRepository.findById(anyLong()))
                .thenReturn(Optional.of(new CustomForm()));

        CustomForm res = formService.findById(anyLong());

        assertNotNull(res);
        assertInstanceOf(CustomForm.class, res);
    }

    @Test
    void findAllFieldsBySpatialUnitId_null() {
        when(formRepository.findById(anyLong()))
                .thenReturn(Optional.empty());

        CustomForm res = formService.findById(anyLong());
        assertNull(res);
    }

    @Test
    void returnsTypeSpecificFormWhenPresent() {
        setUpForReturnTypeSpecificTests();
        CustomForm typeSpecific = new CustomForm();
        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.of(typeSpecific));

        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitType, institution);

        assertSame(typeSpecific, result, "Should return the type-specific form");
        verify(formRepository).findEffectiveFormByTypeAndInstitution(101L, 55L);
        verify(formRepository, never()).findEffectiveFormByTypeAndInstitution(isNull(), eq(55L));
        verifyNoMoreInteractions(formRepository);
    }

    @Test
    void fallsBackToInstitutionOnlyWhenTypeSpecificMissing() {
        setUpForReturnTypeSpecificTests();
        CustomForm fallback = new CustomForm();
        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.empty());
        given(formRepository.findEffectiveFormByTypeAndInstitution(null, 55L))
                .willReturn(Optional.of(fallback));

        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitType, institution);

        assertSame(fallback, result, "Should return the institution-only form on fallback");
        verify(formRepository).findEffectiveFormByTypeAndInstitution(101L, 55L);
        verify(formRepository).findEffectiveFormByTypeAndInstitution(isNull(), eq(55L));
        verifyNoMoreInteractions(formRepository);
    }

    @Test
    void returnsNullWhenNothingFound() {
        setUpForReturnTypeSpecificTests();
        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.empty());
        given(formRepository.findEffectiveFormByTypeAndInstitution(null, 55L))
                .willReturn(Optional.empty());

        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitType, institution);

        assertNull(result, "Should return null when neither lookup finds a form");
        verify(formRepository).findEffectiveFormByTypeAndInstitution(101L, 55L);
        verify(formRepository).findEffectiveFormByTypeAndInstitution(isNull(), eq(55L));
        verifyNoMoreInteractions(formRepository);
    }

    // -----------------------------------------------------------------------
    // Added tests for initOrReuseResponse + updateJpaEntityFromResponse
    // -----------------------------------------------------------------------

    /**
     * Simple JPA-like entity with bindable fields + JavaBean getters/setters.
     * FormService uses reflection + PropertyDescriptor, so names must match.
     */
    public static class DummyEntity {
        private String title;
        private Integer count;
        private OffsetDateTime createdAt;
        private Concept typeConcept;

        public List<String> getBindableFieldNames() {
            return List.of("title", "count", "createdAt", "typeConcept");
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public OffsetDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(OffsetDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Concept getTypeConcept() {
            return typeConcept;
        }

        public void setTypeConcept(Concept typeConcept) {
            this.typeConcept = typeConcept;
        }
    }

    private static CustomField mockSystemField(long id, boolean isSystem, String binding) {
        CustomField field = mock(CustomField.class);
        when(field.getIsSystemField()).thenReturn(isSystem);
        when(field.getValueBinding()).thenReturn(binding);
        return field;
    }

    @Test
    void initOrReuseResponse_reusesExistingAnswer_whenNotForceInit() {
        // arrange
        FieldSource fieldSource = mock(FieldSource.class);
        CustomField field1 = mock(CustomField.class);

        when(fieldSource.getAllFields()).thenReturn(List.of(field1));

        CustomFormResponse existing = new CustomFormResponse();
        Map<CustomField, CustomFieldAnswer> answers = new HashMap<>();
        CustomFieldAnswerText existingAnswer = new CustomFieldAnswerText();
        answers.put(field1, existingAnswer);
        existing.setAnswers(answers);

        DummyEntity entity = new DummyEntity();
        entity.setTitle("shouldNotBeApplied");

        try (MockedStatic<CustomFieldAnswerFactory> mocked = mockStatic(CustomFieldAnswerFactory.class)) {
            // act
            CustomFormResponse res = formService.initOrReuseResponse(existing, entity, fieldSource, false);

            // assert
            assertSame(existing, res);
            assertSame(existingAnswer, res.getAnswers().get(field1), "Existing answer must be reused");
            mocked.verifyNoInteractions(); // should not instantiate anything if answer already exists
        }
    }

    @Test
    void initOrReuseResponse_forceInit_rebuildsAnswer() {
        // arrange
        FieldSource fieldSource = mock(FieldSource.class);
        CustomField field1 = mockSystemField(1L, true, "title");
        when(fieldSource.getAllFields()).thenReturn(List.of(field1));

        CustomFormResponse existing = new CustomFormResponse();
        Map<CustomField, CustomFieldAnswer> answers = new HashMap<>();
        CustomFieldAnswerText existingAnswer = new CustomFieldAnswerText();
        existingAnswer.setValue("old");
        answers.put(field1, existingAnswer);
        existing.setAnswers(answers);

        DummyEntity entity = new DummyEntity();
        entity.setTitle("newTitle");

        CustomFieldAnswerText freshAnswer = new CustomFieldAnswerText();

        try (MockedStatic<CustomFieldAnswerFactory> mocked = mockStatic(CustomFieldAnswerFactory.class)) {
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(field1)).thenReturn(freshAnswer);

            // act
            CustomFormResponse res = formService.initOrReuseResponse(existing, entity, fieldSource, true);

            // assert
            assertNotNull(res.getAnswers());
            assertSame(freshAnswer, res.getAnswers().get(field1), "Answer should be replaced when forceInit=true");
            assertEquals("newTitle", ((CustomFieldAnswerText) res.getAnswers().get(field1)).getValue(),
                    "System field value should be populated from entity");
        }
    }

    @Test
    void initOrReuseResponse_populatesSystemFields_fromEntity_string_integer_datetime() {
        // arrange
        FieldSource fieldSource = mock(FieldSource.class);

        CustomField titleField = mockSystemField(1L, true, "title");
        CustomField countField = mockSystemField(2L, true, "count");
        CustomField createdAtField = mockSystemField(3L, true, "createdAt");

        when(fieldSource.getAllFields()).thenReturn(List.of(titleField, countField, createdAtField));

        DummyEntity entity = new DummyEntity();
        entity.setTitle("Hello");
        entity.setCount(7);
        entity.setCreatedAt(OffsetDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC));

        CustomFieldAnswerText titleAnswer = new CustomFieldAnswerText();
        CustomFieldAnswerInteger countAnswer = new CustomFieldAnswerInteger();
        CustomFieldAnswerDateTime createdAtAnswer = new CustomFieldAnswerDateTime();

        try (MockedStatic<CustomFieldAnswerFactory> mocked = mockStatic(CustomFieldAnswerFactory.class)) {
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(titleField)).thenReturn(titleAnswer);
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(countField)).thenReturn(countAnswer);
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(createdAtField)).thenReturn(createdAtAnswer);

            // act
            CustomFormResponse res = formService.initOrReuseResponse(null, entity, fieldSource, false);

            // assert
            assertEquals("Hello", ((CustomFieldAnswerText) res.getAnswers().get(titleField)).getValue());
            assertEquals(7, ((CustomFieldAnswerInteger) res.getAnswers().get(countField)).getValue());

            LocalDateTime expectedLocal = entity.getCreatedAt().toLocalDateTime();
            assertEquals(expectedLocal, ((CustomFieldAnswerDateTime) res.getAnswers().get(createdAtField)).getValue());

            // also ensure pk set + hasBeenModified false
            assertNotNull(res.getAnswers().get(titleField).getPk());
            assertFalse(res.getAnswers().get(titleField).getHasBeenModified());
        }
    }

    @Test
    void initOrReuseResponse_populatesConceptSystemField_andUiVal() {
        // arrange
        FieldSource fieldSource = mock(FieldSource.class);
        CustomField conceptField = mockSystemField(10L, true, "typeConcept");
        when(fieldSource.getAllFields()).thenReturn(List.of(conceptField));

        DummyEntity entity = new DummyEntity();

        Concept concept = mock(Concept.class);
        entity.setTypeConcept(concept);

        given(labelBean.findLabelOf(concept)).willReturn("My Label");
        given(labelBean.getCurrentUserLang()).willReturn("en");

        CustomFieldAnswerSelectOneFromFieldCode conceptAnswer = new CustomFieldAnswerSelectOneFromFieldCode();

        try (MockedStatic<CustomFieldAnswerFactory> mocked = mockStatic(CustomFieldAnswerFactory.class)) {
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(conceptField)).thenReturn(conceptAnswer);

            // act
            CustomFormResponse res = formService.initOrReuseResponse(null, entity, fieldSource, false);

            // assert
            CustomFieldAnswerSelectOneFromFieldCode stored =
                    (CustomFieldAnswerSelectOneFromFieldCode) res.getAnswers().get(conceptField);

            assertSame(concept, stored.getValue());
            assertNotNull(stored.getUiVal());
            assertEquals(concept, stored.getUiVal().concept());
            assertEquals("My Label", stored.getUiVal().getConceptLabelToDisplay().getLabel());
            assertEquals("en", stored.getUiVal().getConceptLabelToDisplay().getLangCode());
        }
    }

    @Test
    void updateJpaEntityFromResponse_setsBindableSystemFields() {
        // arrange
        DummyEntity entity = new DummyEntity();

        CustomField titleField = mockSystemField(1L, true, "title");
        CustomField countField = mockSystemField(2L, true, "count");
        CustomField createdAtField = mockSystemField(3L, true, "createdAt");

        CustomFieldAnswerText titleAnswer = new CustomFieldAnswerText();
        titleAnswer.setValue("Updated");

        CustomFieldAnswerInteger countAnswer = new CustomFieldAnswerInteger();
        countAnswer.setValue(99);

        CustomFieldAnswerDateTime createdAtAnswer = new CustomFieldAnswerDateTime();
        createdAtAnswer.setValue(LocalDateTime.of(2022, 5, 6, 7, 8, 9));

        CustomFormResponse response = new CustomFormResponse();
        Map<CustomField, CustomFieldAnswer> answers = new HashMap<>();
        answers.put(titleField, titleAnswer);
        answers.put(countField, countAnswer);
        answers.put(createdAtField, createdAtAnswer);
        response.setAnswers(answers);

        // act
        formService.updateJpaEntityFromResponse(response, entity);

        // assert
        assertEquals("Updated", entity.getTitle());
        assertEquals(99, entity.getCount());
        assertEquals(OffsetDateTime.of(2022, 5, 6, 7, 8, 9, 0, ZoneOffset.UTC), entity.getCreatedAt());
    }

    @Test
    void updateJpaEntityFromResponse_ignoresNonSystemOrNonBindableOrNull() {
        // arrange
        DummyEntity entity = new DummyEntity();
        entity.setTitle("initial");

        // non-system field -> should be ignored
        CustomField nonSystemTitle = mock(CustomField.class);
        when(nonSystemTitle.getIsSystemField()).thenReturn(false);
        CustomFieldAnswerText nonSystemAnswer = new CustomFieldAnswerText();
        nonSystemAnswer.setValue("shouldNotApply");


        CustomField wrongBinding = mock(CustomField.class);
        when(wrongBinding.getIsSystemField()).thenReturn(true);
        when(wrongBinding.getValueBinding()).thenReturn("notInBindableList");


        CustomFieldAnswerText wrongBindingAnswer = new CustomFieldAnswerText();
        wrongBindingAnswer.setValue("shouldNotApply");

        // null value -> should not overwrite
        CustomField nullValueField = mockSystemField(3L, true, "title");
        CustomFieldAnswerText nullValueAnswer = new CustomFieldAnswerText();
        nullValueAnswer.setValue(null);

        CustomFormResponse response = new CustomFormResponse();
        response.setAnswers(Map.of(
                nonSystemTitle, nonSystemAnswer,
                wrongBinding, wrongBindingAnswer,
                nullValueField, nullValueAnswer
        ));

        // act
        formService.updateJpaEntityFromResponse(response, entity);

        // assert
        assertEquals("initial", entity.getTitle(), "Title must remain unchanged");
    }
}
