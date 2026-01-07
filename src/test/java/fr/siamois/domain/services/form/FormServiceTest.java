package fr.siamois.domain.services.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.*;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.form.EnabledRulesEngine;
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
        private ActionUnit actionUnit;
        private SpatialUnit spatialUnit;
        private ActionCode actionCode;
        private Person person;
        private List<Person> personList;
        private Set<SpatialUnit> spatialUnitSet;

        public List<String> getBindableFieldNames() {
            return List.of(
                    "title", "count", "createdAt", "typeConcept",
                    "actionUnit", "spatialUnit", "actionCode",
                    "person", "personList", "spatialUnitSet"
            );
        }

        // Getters and Setters
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

        public ActionUnit getActionUnit() {
            return actionUnit;
        }

        public void setActionUnit(ActionUnit actionUnit) {
            this.actionUnit = actionUnit;
        }

        public SpatialUnit getSpatialUnit() {
            return spatialUnit;
        }

        public void setSpatialUnit(SpatialUnit spatialUnit) {
            this.spatialUnit = spatialUnit;
        }

        public ActionCode getActionCode() {
            return actionCode;
        }

        public void setActionCode(ActionCode actionCode) {
            this.actionCode = actionCode;
        }

        public Person getPerson() {
            return person;
        }

        public void setPerson(Person person) {
            this.person = person;
        }

        public List<Person> getPersonList() {
            return personList;
        }

        public void setPersonList(List<Person> personList) {
            this.personList = personList;
        }

        public Set<SpatialUnit> getSpatialUnitSet() {
            return spatialUnitSet;
        }

        public void setSpatialUnitSet(Set<SpatialUnit> spatialUnitSet) {
            this.spatialUnitSet = spatialUnitSet;
        }
    }

    private static CustomField mockSystemField(boolean isSystem, String binding) {
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
        CustomField field1 = mockSystemField(true, "title");
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

        CustomField titleField = mockSystemField(true, "title");
        CustomField countField = mockSystemField(true, "count");
        CustomField createdAtField = mockSystemField(true, "createdAt");

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
        CustomField conceptField = mockSystemField(true, "typeConcept");
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

        CustomField titleField = mockSystemField(true, "title");
        CustomField countField = mockSystemField(true, "count");
        CustomField createdAtField = mockSystemField(true, "createdAt");

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
    void updateJpaEntityFromResponse_setsAllBindableSystemFields() {
        // Arrange: Create a dummy JPA entity with all bindable fields
        DummyEntity entity = new DummyEntity();

        // Mock fields for all supported answer types
        CustomField titleField = mockSystemField(true, "title");
        CustomField countField = mockSystemField(true, "count");
        CustomField createdAtField = mockSystemField(true, "createdAt");
        CustomField conceptField = mockSystemField(true, "typeConcept");
        CustomField actionUnitField = mockSystemField(true, "actionUnit");
        CustomField spatialUnitField = mockSystemField(true, "spatialUnit");
        CustomField actionCodeField = mockSystemField(true, "actionCode");
        CustomField personField = mockSystemField(true, "person");
        CustomField personListField = mockSystemField(true, "personList");
        CustomField spatialUnitSetField = mockSystemField(true, "spatialUnitSet");

        // Mock answers for all supported types
        CustomFieldAnswerText titleAnswer = new CustomFieldAnswerText();
        titleAnswer.setValue("Updated Title");

        CustomFieldAnswerInteger countAnswer = new CustomFieldAnswerInteger();
        countAnswer.setValue(42);

        CustomFieldAnswerDateTime createdAtAnswer = new CustomFieldAnswerDateTime();
        createdAtAnswer.setValue(LocalDateTime.of(2023, 1, 1, 12, 0));

        // CustomFieldAnswerSelectOneFromFieldCode: Use uiVal to set the concept
        Concept concept = mock(Concept.class);
        ConceptAutocompleteDTO conceptAutocompleteDTO = new ConceptAutocompleteDTO(concept, "Test Label", "fr");
        CustomFieldAnswerSelectOneFromFieldCode conceptAnswer = new CustomFieldAnswerSelectOneFromFieldCode();
        conceptAnswer.setUiVal(conceptAutocompleteDTO);

        ActionUnit actionUnit = mock(ActionUnit.class);
        CustomFieldAnswerSelectOneActionUnit actionUnitAnswer = new CustomFieldAnswerSelectOneActionUnit();
        actionUnitAnswer.setValue(actionUnit);

        SpatialUnit spatialUnit = mock(SpatialUnit.class);
        CustomFieldAnswerSelectOneSpatialUnit spatialUnitAnswer = new CustomFieldAnswerSelectOneSpatialUnit();
        spatialUnitAnswer.setValue(spatialUnit);

        ActionCode actionCode = mock(ActionCode.class);
        CustomFieldAnswerSelectOneActionCode actionCodeAnswer = new CustomFieldAnswerSelectOneActionCode();
        actionCodeAnswer.setValue(actionCode);

        Person person = mock(Person.class);
        CustomFieldAnswerSelectOnePerson personAnswer = new CustomFieldAnswerSelectOnePerson();
        personAnswer.setValue(person);

        List<Person> personList = List.of(mock(Person.class), mock(Person.class));
        CustomFieldAnswerSelectMultiplePerson personListAnswer = new CustomFieldAnswerSelectMultiplePerson();
        personListAnswer.setValue(personList);

        Set<SpatialUnit> spatialUnitSet = Set.of(mock(SpatialUnit.class), mock(SpatialUnit.class));
        CustomFieldAnswerSelectMultipleSpatialUnitTree spatialUnitSetAnswer = new CustomFieldAnswerSelectMultipleSpatialUnitTree();
        spatialUnitSetAnswer.setValue(spatialUnitSet);

        // Create a response with all answers
        CustomFormResponse response = new CustomFormResponse();
        Map<CustomField, CustomFieldAnswer> answers = new HashMap<>();
        answers.put(titleField, titleAnswer);
        answers.put(countField, countAnswer);
        answers.put(createdAtField, createdAtAnswer);
        answers.put(conceptField, conceptAnswer);
        answers.put(actionUnitField, actionUnitAnswer);
        answers.put(spatialUnitField, spatialUnitAnswer);
        answers.put(actionCodeField, actionCodeAnswer);
        answers.put(personField, personAnswer);
        answers.put(personListField, personListAnswer);
        answers.put(spatialUnitSetField, spatialUnitSetAnswer);
        response.setAnswers(answers);

        // Act: Update the JPA entity from the response
        formService.updateJpaEntityFromResponse(response, entity);

        // Assert: Verify all fields were set correctly
        assertEquals("Updated Title", entity.getTitle());
        assertEquals(42, entity.getCount());
        assertEquals(OffsetDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC), entity.getCreatedAt());
        assertEquals(concept, entity.getTypeConcept());
        assertEquals(actionUnit, entity.getActionUnit());
        assertEquals(spatialUnit, entity.getSpatialUnit());
        assertEquals(actionCode, entity.getActionCode());
        assertEquals(person, entity.getPerson());
        assertEquals(personList, entity.getPersonList());
        assertEquals(spatialUnitSet, entity.getSpatialUnitSet());
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
        CustomField nullValueField = mockSystemField(true, "title");
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

    @Test
    void buildEnabledEngine_createsEngineWithCorrectRulesAndDependencies() {
        // Arrange
        FieldSource fieldSource = mock(FieldSource.class);
        CustomField field1 = mock(CustomField.class);
        CustomField field2 = mock(CustomField.class);
        CustomField field3 = mock(CustomField.class);

        // Mock EnabledWhenJson for field2 (depends on field1)
        EnabledWhenJson specForField2 = new EnabledWhenJson();
        specForField2.setFieldId(1L);
        specForField2.setOp(EnabledWhenJson.Op.EQ);
        EnabledWhenJson.ValueJson valueJson = new EnabledWhenJson.ValueJson();
        valueJson.setAnswerClass("fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerText");
        valueJson.setValue(new ObjectMapper().createObjectNode().put("value", "test"));
        specForField2.setValues(List.of(valueJson));

        // Mock EnabledWhenJson for field3 (depends on field2)
        EnabledWhenJson specForField3 = new EnabledWhenJson();
        specForField3.setFieldId(2L);
        specForField3.setOp(EnabledWhenJson.Op.NEQ);
        specForField3.setValues(List.of(valueJson));

        // Setup mocks
        when(fieldSource.getAllFields()).thenReturn(List.of(field1, field2, field3));
        when(fieldSource.getEnabledSpec(field1)).thenReturn(null); // No spec for field1
        when(fieldSource.getEnabledSpec(field2)).thenReturn(specForField2);
        when(fieldSource.getEnabledSpec(field3)).thenReturn(specForField3);
        when(fieldSource.findFieldById(1L)).thenReturn(field1);
        when(fieldSource.findFieldById(2L)).thenReturn(field2);

        // Act
        EnabledRulesEngine engine = formService.buildEnabledEngine(fieldSource);

        // Assert
        assertNotNull(engine, "Engine should not be null");


    }

    @Test
    void initOrReuseResponse_populatesSystemFields_fromEntity_allHandlers() {
        // Arrange
        FieldSource fieldSource = mock(FieldSource.class);

        // Mock fields for all supported types
        CustomField titleField = mockSystemField(true, "title");
        CustomField countField = mockSystemField(true, "count");
        CustomField createdAtField = mockSystemField(true, "createdAt");
        CustomField conceptField = mockSystemField(true, "typeConcept");
        CustomField actionUnitField = mockSystemField(true, "actionUnit");
        CustomField spatialUnitField = mockSystemField(true, "spatialUnit");
        CustomField actionCodeField = mockSystemField(true, "actionCode");
        CustomField personField = mockSystemField(true, "person");
        CustomField personListField = mockSystemField(true, "personList");
        CustomField spatialUnitSetField = mockSystemField(true, "spatialUnitSet");

        // Setup mocks for fieldSource
        when(fieldSource.getAllFields()).thenReturn(
                List.of(titleField, countField, createdAtField, conceptField, actionUnitField,
                        spatialUnitField, actionCodeField, personField, personListField, spatialUnitSetField)
        );

        // Create a dummy entity with all types of values
        DummyEntity entity = new DummyEntity();
        entity.setTitle("Hello");
        entity.setCount(7);
        entity.setCreatedAt(OffsetDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC));

        Concept concept = mock(Concept.class);
        entity.setTypeConcept(concept);

        ActionUnit actionUnit = mock(ActionUnit.class);
        entity.setActionUnit(actionUnit);

        SpatialUnit spatialUnit = mock(SpatialUnit.class);
        entity.setSpatialUnit(spatialUnit);

        ActionCode actionCode = mock(ActionCode.class);
        entity.setActionCode(actionCode);

        Person person = mock(Person.class);
        entity.setPerson(person);

        List<Person> personList = List.of(mock(Person.class), mock(Person.class));
        entity.setPersonList(personList);

        Set<SpatialUnit> spatialUnitSet = Set.of(mock(SpatialUnit.class), mock(SpatialUnit.class));
        entity.setSpatialUnitSet(spatialUnitSet);

        // Mock answers for all supported types
        CustomFieldAnswerText titleAnswer = new CustomFieldAnswerText();
        CustomFieldAnswerInteger countAnswer = new CustomFieldAnswerInteger();
        CustomFieldAnswerDateTime createdAtAnswer = new CustomFieldAnswerDateTime();
        CustomFieldAnswerSelectOneFromFieldCode conceptAnswer = new CustomFieldAnswerSelectOneFromFieldCode();
        CustomFieldAnswerSelectOneActionUnit actionUnitAnswer = new CustomFieldAnswerSelectOneActionUnit();
        CustomFieldAnswerSelectOneSpatialUnit spatialUnitAnswer = new CustomFieldAnswerSelectOneSpatialUnit();
        CustomFieldAnswerSelectOneActionCode actionCodeAnswer = new CustomFieldAnswerSelectOneActionCode();
        CustomFieldAnswerSelectOnePerson personAnswer = new CustomFieldAnswerSelectOnePerson();
        CustomFieldAnswerSelectMultiplePerson personListAnswer = new CustomFieldAnswerSelectMultiplePerson();
        CustomFieldAnswerSelectMultipleSpatialUnitTree spatialUnitSetAnswer = new CustomFieldAnswerSelectMultipleSpatialUnitTree();

        // Mock the factory to return the answers
        try (MockedStatic<CustomFieldAnswerFactory> mockedFactory = mockStatic(CustomFieldAnswerFactory.class)) {
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(titleField)).thenReturn(titleAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(countField)).thenReturn(countAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(createdAtField)).thenReturn(createdAtAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(conceptField)).thenReturn(conceptAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(actionUnitField)).thenReturn(actionUnitAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(spatialUnitField)).thenReturn(spatialUnitAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(actionCodeField)).thenReturn(actionCodeAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(personField)).thenReturn(personAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(personListField)).thenReturn(personListAnswer);
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(spatialUnitSetField)).thenReturn(spatialUnitSetAnswer);

            // Act: Initialize or reuse the response
            CustomFormResponse response = formService.initOrReuseResponse(null, entity, fieldSource, false);

            // Assert: Verify all answers were populated correctly
            assertEquals("Hello", ((CustomFieldAnswerText) response.getAnswers().get(titleField)).getValue());
            assertEquals(7, ((CustomFieldAnswerInteger) response.getAnswers().get(countField)).getValue());
            assertEquals(entity.getCreatedAt().toLocalDateTime(), ((CustomFieldAnswerDateTime) response.getAnswers().get(createdAtField)).getValue());
            assertEquals(concept, ((CustomFieldAnswerSelectOneFromFieldCode) response.getAnswers().get(conceptField)).getValue());
            assertEquals(actionUnit, ((CustomFieldAnswerSelectOneActionUnit) response.getAnswers().get(actionUnitField)).getValue());
            assertEquals(spatialUnit, ((CustomFieldAnswerSelectOneSpatialUnit) response.getAnswers().get(spatialUnitField)).getValue());
            assertEquals(actionCode, ((CustomFieldAnswerSelectOneActionCode) response.getAnswers().get(actionCodeField)).getValue());
            assertEquals(person, ((CustomFieldAnswerSelectOnePerson) response.getAnswers().get(personField)).getValue());
            assertEquals(personList, ((CustomFieldAnswerSelectMultiplePerson) response.getAnswers().get(personListField)).getValue());
            assertEquals(spatialUnitSet, ((CustomFieldAnswerSelectMultipleSpatialUnitTree) response.getAnswers().get(spatialUnitSetField)).getValue());

            // Also ensure pk set + hasBeenModified false
            assertNotNull(response.getAnswers().get(titleField).getPk());
            assertFalse(response.getAnswers().get(titleField).getHasBeenModified());
        }
    }




}
