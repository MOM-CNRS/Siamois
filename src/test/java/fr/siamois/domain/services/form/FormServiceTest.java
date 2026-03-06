package fr.siamois.domain.services.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.siamois.domain.models.actionunit.ActionCode;
import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.CustomFieldStratigraphy;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.EnabledWhenJson;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.bean.LabelBean;
import fr.siamois.ui.form.CustomFieldAnswerFactory;
import fr.siamois.ui.form.fieldsource.FieldSource;
import fr.siamois.ui.form.rules.EnabledRulesEngine;
import fr.siamois.ui.viewmodel.CustomFormResponseViewModel;
import fr.siamois.ui.viewmodel.fieldanswer.*;
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
import static org.mockito.ArgumentMatchers.anyLong;
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

    void setUpForReturnTypeSpecificTests() {
        Concept recordingUnitType = mock(Concept.class);
        Institution institution = mock(Institution.class);
        ConceptDTO recordingUnitTypeDTO = mock(ConceptDTO.class);
        InstitutionDTO institutionDTO = mock(InstitutionDTO.class);

        // Use deterministic IDs in stubs
        given(recordingUnitType.getId()).willReturn(101L);  // Add this line
        given(recordingUnitTypeDTO.getId()).willReturn(101L); // Add this line
        given(institution.getId()).willReturn(55L);
        given(institutionDTO.getId()).willReturn(55L);
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
        private ConceptDTO typeConcept;
        private ActionUnitDTO actionUnit;
        private SpatialUnitDTO spatialUnit;
        private ActionCode actionCode;
        private PersonDTO person;
        private List<PersonDTO> personList;
        private Set<SpatialUnitDTO> spatialUnitSet;

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

        public ConceptDTO getTypeConcept() {
            return typeConcept;
        }

        public void setTypeConcept(ConceptDTO typeConcept) {
            this.typeConcept = typeConcept;
        }

        public ActionUnitDTO getActionUnit() {
            return actionUnit;
        }

        public void setActionUnit(ActionUnitDTO actionUnit) {
            this.actionUnit = actionUnit;
        }

        public SpatialUnitDTO getSpatialUnit() {
            return spatialUnit;
        }

        public void setSpatialUnit(SpatialUnitDTO spatialUnit) {
            this.spatialUnit = spatialUnit;
        }

        public ActionCode getActionCode() {
            return actionCode;
        }

        public void setActionCode(ActionCode actionCode) {
            this.actionCode = actionCode;
        }

        public PersonDTO getPerson() {
            return person;
        }

        public void setPerson(PersonDTO person) {
            this.person = person;
        }

        public List<PersonDTO> getPersonList() {
            return personList;
        }

        public void setPersonList(List<PersonDTO> personList) {
            this.personList = personList;
        }

        public Set<SpatialUnitDTO> getSpatialUnitSet() {
            return spatialUnitSet;
        }

        public void setSpatialUnitSet(Set<SpatialUnitDTO> spatialUnitSet) {
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

        CustomFormResponseViewModel existing = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        CustomFieldAnswerTextViewModel existingAnswer = new CustomFieldAnswerTextViewModel();
        answers.put(field1, existingAnswer);
        existing.setAnswers(answers);

        DummyEntity entity = new DummyEntity();
        entity.setTitle("shouldNotBeApplied");

        try (MockedStatic<CustomFieldAnswerFactory> mocked = mockStatic(CustomFieldAnswerFactory.class)) {
            // act
            CustomFormResponseViewModel res = formService.initOrReuseResponse(existing, entity, fieldSource, false);

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

        CustomFormResponseViewModel existing = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        CustomFieldAnswerTextViewModel existingAnswer = new CustomFieldAnswerTextViewModel();
        existingAnswer.setValue("old");
        answers.put(field1, existingAnswer);
        existing.setAnswers(answers);

        DummyEntity entity = new DummyEntity();
        entity.setTitle("newTitle");

        CustomFieldAnswerTextViewModel freshAnswer = new CustomFieldAnswerTextViewModel();

        try (MockedStatic<CustomFieldAnswerFactory> mocked = mockStatic(CustomFieldAnswerFactory.class)) {
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(field1)).thenReturn(freshAnswer);

            // act
            CustomFormResponseViewModel res = formService.initOrReuseResponse(existing, entity, fieldSource, true);

            // assert
            assertNotNull(res.getAnswers());
            assertSame(freshAnswer, res.getAnswers().get(field1), "Answer should be replaced when forceInit=true");
            assertEquals("newTitle", freshAnswer.getValue(), "System field value should be populated from entity");
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

        CustomFieldAnswerTextViewModel titleAnswer = new CustomFieldAnswerTextViewModel();
        CustomFieldAnswerIntegerViewModel countAnswer = new CustomFieldAnswerIntegerViewModel();
        CustomFieldAnswerDateTimeViewModel createdAtAnswer = new CustomFieldAnswerDateTimeViewModel();

        try (MockedStatic<CustomFieldAnswerFactory> mocked = mockStatic(CustomFieldAnswerFactory.class)) {
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(titleField)).thenReturn(titleAnswer);
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(countField)).thenReturn(countAnswer);
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(createdAtField)).thenReturn(createdAtAnswer);

            // act
            CustomFormResponseViewModel res = formService.initOrReuseResponse(null, entity, fieldSource, false);

            // assert
            assertEquals("Hello", ((CustomFieldAnswerTextViewModel) res.getAnswers().get(titleField)).getValue());
            assertEquals(7, ((CustomFieldAnswerIntegerViewModel) res.getAnswers().get(countField)).getValue());

            LocalDateTime expectedLocal = entity.getCreatedAt().toLocalDateTime();
            assertEquals(expectedLocal, ((CustomFieldAnswerDateTimeViewModel) res.getAnswers().get(createdAtField)).getValue());

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

        ConceptDTO concept = mock(ConceptDTO.class);
        entity.setTypeConcept(concept);

        // Mock the label bean to return a label for the concept
        given(labelBean.findLabelOf(concept)).willReturn("My Label");
        given(labelBean.getCurrentUserLang()).willReturn("en");

        // Create a ConceptAutocompleteDTO, which is what the view model expects
        ConceptAutocompleteDTO conceptAutocompleteDTO = new ConceptAutocompleteDTO(concept, "My Label", "en");

        // Create a real instance of the view model
        CustomFieldAnswerSelectOneFromFieldCodeViewModel conceptAnswer = new CustomFieldAnswerSelectOneFromFieldCodeViewModel();
        // Set the value directly
        conceptAnswer.setValue(conceptAutocompleteDTO);

        try (MockedStatic<CustomFieldAnswerFactory> mocked = mockStatic(CustomFieldAnswerFactory.class)) {
            // Mock the factory to return the pre-configured answer
            mocked.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(conceptField)).thenReturn(conceptAnswer);

            // act
            CustomFormResponseViewModel res = formService.initOrReuseResponse(null, entity, fieldSource, false);

            // assert
            CustomFieldAnswerSelectOneFromFieldCodeViewModel stored =
                    (CustomFieldAnswerSelectOneFromFieldCodeViewModel) res.getAnswers().get(conceptField);

            assertNotNull(stored.getValue());
            assertEquals(concept, stored.getValue().concept());
            assertEquals("My Label", stored.getValue().getConceptLabelToDisplay().getLabel());
            assertEquals("en", stored.getValue().getConceptLabelToDisplay().getLangCode());
        }
    }


    @Test
    void updateJpaEntityFromResponse_setsBindableSystemFields() {
        // arrange
        DummyEntity entity = new DummyEntity();

        CustomField titleField = mockSystemField(true, "title");
        CustomField countField = mockSystemField(true, "count");
        CustomField createdAtField = mockSystemField(true, "createdAt");

        CustomFieldAnswerTextViewModel  titleAnswer = new CustomFieldAnswerTextViewModel ();
        titleAnswer.setValue("Updated");

        CustomFieldAnswerIntegerViewModel  countAnswer = new CustomFieldAnswerIntegerViewModel ();
        countAnswer.setValue(99);

        CustomFieldAnswerDateTimeViewModel  createdAtAnswer = new CustomFieldAnswerDateTimeViewModel ();
        createdAtAnswer.setValue(LocalDateTime.of(2022, 5, 6, 7, 8, 9));

        CustomFormResponseViewModel  response = new CustomFormResponseViewModel ();
        Map<CustomField, CustomFieldAnswerViewModel > answers = new HashMap<>();
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
        CustomFieldAnswerTextViewModel  titleAnswer = new CustomFieldAnswerTextViewModel();
        titleAnswer.setValue("Updated Title");

        CustomFieldAnswerIntegerViewModel  countAnswer = new CustomFieldAnswerIntegerViewModel ();
        countAnswer.setValue(42);

        CustomFieldAnswerDateTimeViewModel  createdAtAnswer = new CustomFieldAnswerDateTimeViewModel ();
        createdAtAnswer.setValue(LocalDateTime.of(2023, 1, 1, 12, 0));

        // CustomFieldAnswerSelectOneFromFieldCode: Use uiVal to set the concept
        ConceptDTO concept = mock(ConceptDTO.class);
        ConceptAutocompleteDTO conceptAutocompleteDTO = new ConceptAutocompleteDTO(concept, "Test Label", "fr");
        CustomFieldAnswerSelectOneFromFieldCodeViewModel  conceptAnswer = new CustomFieldAnswerSelectOneFromFieldCodeViewModel ();
        conceptAnswer.setValue(conceptAutocompleteDTO);

        ActionUnitDTO actionUnit = mock(ActionUnitDTO.class);
        CustomFieldAnswerSelectOneActionUnitViewModel  actionUnitAnswer = new CustomFieldAnswerSelectOneActionUnitViewModel ();
        actionUnitAnswer.setValue(actionUnit);

        SpatialUnitDTO spatialUnit = mock(SpatialUnitDTO.class);
        CustomFieldAnswerSelectOneSpatialUnitViewModel  spatialUnitAnswer = new CustomFieldAnswerSelectOneSpatialUnitViewModel ();
        spatialUnitAnswer.setValue(spatialUnit);

        ActionCode actionCode = mock(ActionCode.class);
        CustomFieldAnswerSelectOneActionCodeViewModel  actionCodeAnswer = new CustomFieldAnswerSelectOneActionCodeViewModel ();
        actionCodeAnswer.setValue(actionCode);

        PersonDTO person = mock(PersonDTO.class);
        CustomFieldAnswerSelectOnePersonViewModel  personAnswer = new CustomFieldAnswerSelectOnePersonViewModel ();
        personAnswer.setValue(person);

        List<PersonDTO> personList = List.of(mock(PersonDTO.class), mock(PersonDTO.class));
        CustomFieldAnswerSelectMultiplePersonViewModel  personListAnswer = new CustomFieldAnswerSelectMultiplePersonViewModel();
        personListAnswer.setValue(personList);

        Set<SpatialUnitDTO> spatialUnitSet = Set.of(mock(SpatialUnitDTO.class), mock(SpatialUnitDTO.class));
        CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel spatialUnitSetAnswer = new CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel();
        spatialUnitSetAnswer.setValue(spatialUnitSet);

        // Create a response with all answers
        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
        Map<CustomField, CustomFieldAnswerViewModel > answers = new HashMap<>();
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
        CustomFieldAnswerTextViewModel nonSystemAnswer = new CustomFieldAnswerTextViewModel();
        nonSystemAnswer.setValue("shouldNotApply");


        CustomField wrongBinding = mock(CustomField.class);
        when(wrongBinding.getIsSystemField()).thenReturn(true);
        when(wrongBinding.getValueBinding()).thenReturn("notInBindableList");


        CustomFieldAnswerTextViewModel wrongBindingAnswer = new CustomFieldAnswerTextViewModel();
        wrongBindingAnswer.setValue("shouldNotApply");

        // null value -> should not overwrite
        CustomField nullValueField = mockSystemField(true, "title");
        CustomFieldAnswerTextViewModel nullValueAnswer = new CustomFieldAnswerTextViewModel();
        nullValueAnswer.setValue(null);

        CustomFormResponseViewModel response = new CustomFormResponseViewModel();
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

        ConceptDTO concept = mock(ConceptDTO.class);
        entity.setTypeConcept(concept);

        ActionUnitDTO actionUnit = mock(ActionUnitDTO.class);
        entity.setActionUnit(actionUnit);

        SpatialUnitDTO spatialUnit = mock(SpatialUnitDTO.class);
        entity.setSpatialUnit(spatialUnit);

        ActionCode actionCode = mock(ActionCode.class);
        entity.setActionCode(actionCode);

        PersonDTO person = mock(PersonDTO.class);
        entity.setPerson(person);

        // Create a mutable list for personList
        List<PersonDTO> personList = new ArrayList<>();
        PersonDTO person1 = mock(PersonDTO.class);
        PersonDTO person2 = mock(PersonDTO.class);
        personList.add(person1);
        personList.add(person2);
        entity.setPersonList(personList);

        Set<SpatialUnitDTO> spatialUnitSet = Set.of(mock(SpatialUnitDTO.class), mock(SpatialUnitDTO.class));
        entity.setSpatialUnitSet(spatialUnitSet);

        // Mock the label bean to return a label for the concept
        given(labelBean.findLabelOf(concept)).willReturn("Concept Label");
        given(labelBean.getCurrentUserLang()).willReturn("en");

        // Mock the factory to return the answers
        try (MockedStatic<CustomFieldAnswerFactory> mockedFactory = mockStatic(CustomFieldAnswerFactory.class)) {
            // Mock the factory to return a CustomFieldAnswerSelectMultiplePersonViewModel with the personList set
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(personListField))
                    .thenAnswer(invocation -> {
                        CustomFieldAnswerSelectMultiplePersonViewModel answer = new CustomFieldAnswerSelectMultiplePersonViewModel();
                        answer.setValue(entity.getPersonList());
                        return answer;
                    });

            // Mock other fields
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(titleField))
                    .thenReturn(new CustomFieldAnswerTextViewModel());
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(countField))
                    .thenReturn(new CustomFieldAnswerIntegerViewModel());
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(createdAtField))
                    .thenReturn(new CustomFieldAnswerDateTimeViewModel());
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(conceptField))
                    .thenReturn(new CustomFieldAnswerSelectOneFromFieldCodeViewModel());
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(actionUnitField))
                    .thenReturn(new CustomFieldAnswerSelectOneActionUnitViewModel());
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(spatialUnitField))
                    .thenReturn(new CustomFieldAnswerSelectOneSpatialUnitViewModel());
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(actionCodeField))
                    .thenReturn(new CustomFieldAnswerSelectOneActionCodeViewModel());
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(personField))
                    .thenReturn(new CustomFieldAnswerSelectOnePersonViewModel());
            mockedFactory.when(() -> CustomFieldAnswerFactory.instantiateAnswerForField(spatialUnitSetField))
                    .thenReturn(new CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel());

            // Act: Initialize or reuse the response
            CustomFormResponseViewModel response = formService.initOrReuseResponse(null, entity, fieldSource, false);

            // Assert: Verify all answers were populated correctly
            assertEquals("Hello", ((CustomFieldAnswerTextViewModel) response.getAnswers().get(titleField)).getValue());
            assertEquals(7, ((CustomFieldAnswerIntegerViewModel) response.getAnswers().get(countField)).getValue());
            assertEquals(entity.getCreatedAt().toLocalDateTime(), ((CustomFieldAnswerDateTimeViewModel) response.getAnswers().get(createdAtField)).getValue());
            assertEquals(concept, ((CustomFieldAnswerSelectOneFromFieldCodeViewModel) response.getAnswers().get(conceptField)).getValue().concept());
            assertEquals("Concept Label", ((CustomFieldAnswerSelectOneFromFieldCodeViewModel) response.getAnswers().get(conceptField)).getValue().getConceptLabelToDisplay().getLabel());
            assertEquals("en", ((CustomFieldAnswerSelectOneFromFieldCodeViewModel) response.getAnswers().get(conceptField)).getValue().getConceptLabelToDisplay().getLangCode());
            assertEquals(actionUnit, ((CustomFieldAnswerSelectOneActionUnitViewModel) response.getAnswers().get(actionUnitField)).getValue());
            assertEquals(spatialUnit, ((CustomFieldAnswerSelectOneSpatialUnitViewModel) response.getAnswers().get(spatialUnitField)).getValue());
            assertEquals(actionCode, ((CustomFieldAnswerSelectOneActionCodeViewModel) response.getAnswers().get(actionCodeField)).getValue());
            assertEquals(person, ((CustomFieldAnswerSelectOnePersonViewModel) response.getAnswers().get(personField)).getValue());
            assertEquals(personList, ((CustomFieldAnswerSelectMultiplePersonViewModel) response.getAnswers().get(personListField)).getValue());
            assertEquals(spatialUnitSet, ((CustomFieldAnswerSelectMultipleSpatialUnitTreeViewModel) response.getAnswers().get(spatialUnitSetField)).getValue());

            // Also ensure pk set + hasBeenModified false
            assertNotNull(response.getAnswers().get(titleField).getPk());
            assertFalse(response.getAnswers().get(titleField).getHasBeenModified());
        }
    }

    // Helper method to create a RecordingUnitDTO with a specific ID
    private RecordingUnitDTO createRecordingUnitDTO(Long id) {
        RecordingUnitDTO unit = new RecordingUnitDTO();
        unit.setId(id);
        unit.setRelationshipsAsUnit1(new HashSet<>());
        unit.setRelationshipsAsUnit2(new HashSet<>());
        return unit;
    }

    // Helper method to create a StratigraphicRelationshipDTO with specific units
    private StratigraphicRelationshipDTO createStratigraphicRelationshipDTO(RecordingUnitDTO unit1, RecordingUnitDTO unit2) {
        StratigraphicRelationshipDTO rel = new StratigraphicRelationshipDTO();
        rel.setUnit1(new RecordingUnitSummaryDTO(unit1));
        rel.setUnit2(new RecordingUnitSummaryDTO(unit2));
        return rel;
    }

    private CustomFormResponseViewModel createResponse(CustomFieldAnswerStratigraphyViewModel stratiAnswer) {
        CustomFormResponseViewModel response = new CustomFormResponseViewModel();

        CustomFieldStratigraphy field = new CustomFieldStratigraphy();

        Map<CustomField, CustomFieldAnswerViewModel> answers = new HashMap<>();
        answers.put(field, stratiAnswer);

        response.setAnswers(answers);

        return response;
    }

    @Test
    void updateJpaEntityFromResponse_AddsAnteriorRelationshipsCorrectly() {

        RecordingUnitDTO entity = createRecordingUnitDTO(1L);
        RecordingUnitDTO unit2 = createRecordingUnitDTO(2L);

        CustomFieldAnswerStratigraphyViewModel stratiAnswer = new CustomFieldAnswerStratigraphyViewModel();

        StratigraphicRelationshipDTO rel1 = createStratigraphicRelationshipDTO(entity, unit2);
        StratigraphicRelationshipDTO rel2 = createStratigraphicRelationshipDTO(unit2, entity);

        stratiAnswer.getAnteriorRelationships().add(rel1);
        stratiAnswer.getPosteriorRelationships().add(rel2);

        CustomFormResponseViewModel response = createResponse(stratiAnswer);

        formService.updateJpaEntityFromResponse(response, entity);

        assertEquals(1, entity.getRelationshipsAsUnit1().size());
        assertTrue(entity.getRelationshipsAsUnit1().contains(rel1));

        assertEquals(1, entity.getRelationshipsAsUnit2().size());
        assertTrue(entity.getRelationshipsAsUnit2().contains(rel2));
    }

    @Test
    void updateJpaEntityFromResponse_AddsPosteriorRelationshipsCorrectly() {
        // Arrange
        RecordingUnitDTO entity = createRecordingUnitDTO(1L);
        RecordingUnitDTO unit2 = createRecordingUnitDTO(2L);

        CustomFieldAnswerStratigraphyViewModel stratiAnswer = new CustomFieldAnswerStratigraphyViewModel();

        StratigraphicRelationshipDTO rel1 = createStratigraphicRelationshipDTO(entity, unit2);
        StratigraphicRelationshipDTO rel2 = createStratigraphicRelationshipDTO(unit2, entity);

        stratiAnswer.getPosteriorRelationships().add(rel1);
        stratiAnswer.getAnteriorRelationships().add(rel2);

        CustomFormResponseViewModel response = createResponse(stratiAnswer);

        // Act
        formService.updateJpaEntityFromResponse(response, entity);

        // Assert
        assertEquals(1, entity.getRelationshipsAsUnit1().size());
        assertTrue(entity.getRelationshipsAsUnit1().contains(rel1));

        assertEquals(1, entity.getRelationshipsAsUnit2().size());
        assertTrue(entity.getRelationshipsAsUnit2().contains(rel2));
    }

    @Test
    void updateJpaEntityFromResponse_AddsSynchronousRelationshipsCorrectly() {

        RecordingUnitDTO entity = createRecordingUnitDTO(1L);
        RecordingUnitDTO unit2 = createRecordingUnitDTO(2L);

        CustomFieldAnswerStratigraphyViewModel stratiAnswer = new CustomFieldAnswerStratigraphyViewModel();

        StratigraphicRelationshipDTO rel1 = createStratigraphicRelationshipDTO(entity, unit2);
        StratigraphicRelationshipDTO rel2 = createStratigraphicRelationshipDTO(unit2, entity);

        stratiAnswer.getSynchronousRelationships().add(rel1);
        stratiAnswer.getSynchronousRelationships().add(rel2);

        CustomFormResponseViewModel response = createResponse(stratiAnswer);

        formService.updateJpaEntityFromResponse(response, entity);

        assertEquals(1, entity.getRelationshipsAsUnit1().size());
        assertTrue(entity.getRelationshipsAsUnit1().contains(rel1));

        assertEquals(1, entity.getRelationshipsAsUnit2().size());
        assertTrue(entity.getRelationshipsAsUnit2().contains(rel2));
    }

    @Test
    void updateJpaEntityFromResponse_ClearsExistingRelationships() {

        RecordingUnitDTO entity = createRecordingUnitDTO(1L);
        RecordingUnitDTO unit2 = createRecordingUnitDTO(2L);

        StratigraphicRelationshipDTO existingRel = createStratigraphicRelationshipDTO(entity, unit2);
        entity.getRelationshipsAsUnit1().add(existingRel);

        CustomFieldAnswerStratigraphyViewModel stratiAnswer = new CustomFieldAnswerStratigraphyViewModel();

        CustomFormResponseViewModel response = createResponse(stratiAnswer);

        formService.updateJpaEntityFromResponse(response, entity);

        assertTrue(entity.getRelationshipsAsUnit1().isEmpty());
        assertTrue(entity.getRelationshipsAsUnit2().isEmpty());
    }

    private FieldSource fieldSourceWith(CustomField field) {
        FieldSource fs = mock(FieldSource.class);
        when(fs.getAllFields()).thenReturn(List.of(field));
        return fs;
    }
    private StratigraphicRelationshipDTO createRelationship(
            RecordingUnitDTO u1,
            RecordingUnitDTO u2,
            Boolean async
    ) {
        StratigraphicRelationshipDTO rel = new StratigraphicRelationshipDTO();
        rel.setUnit1(new RecordingUnitSummaryDTO(u1));
        rel.setUnit2(new RecordingUnitSummaryDTO(u2));
        rel.setIsAsynchronous(async);
        return rel;
    }
    private CustomField createStratigraphyField() {
        CustomFieldStratigraphy field = new CustomFieldStratigraphy();
        return field;
    }
    @Test
    void initOrReuseResponse_collectsSynchronousRelationships() {

        RecordingUnitDTO unit1 = createRecordingUnitDTO(1L);
        RecordingUnitDTO unit2 = createRecordingUnitDTO(2L);

        StratigraphicRelationshipDTO rel1 = createRelationship(unit1, unit2, false);
        StratigraphicRelationshipDTO rel2 = createRelationship(unit2, unit1, false);

        unit1.getRelationshipsAsUnit1().add(rel1);
        unit1.getRelationshipsAsUnit2().add(rel2);

        CustomField field = createStratigraphyField();
        FieldSource fs = fieldSourceWith(field);

        CustomFormResponseViewModel response =
                formService.initOrReuseResponse(null, unit1, fs, false);

        CustomFieldAnswerStratigraphyViewModel answer =
                (CustomFieldAnswerStratigraphyViewModel) response.getAnswers().get(field);

        assertEquals(2, answer.getSynchronousRelationships().size());
        assertTrue(answer.getAnteriorRelationships().isEmpty());
        assertTrue(answer.getPosteriorRelationships().isEmpty());
    }
    @Test
    void initOrReuseResponse_addsPosteriorRelationships() {

        RecordingUnitDTO unit1 = createRecordingUnitDTO(1L);
        RecordingUnitDTO unit2 = createRecordingUnitDTO(2L);

        StratigraphicRelationshipDTO rel = createRelationship(unit1, unit2, true);

        unit1.getRelationshipsAsUnit1().add(rel);

        CustomField field = createStratigraphyField();
        FieldSource fs = fieldSourceWith(field);

        CustomFormResponseViewModel response =
                formService.initOrReuseResponse(null, unit1, fs, false);

        CustomFieldAnswerStratigraphyViewModel answer =
                (CustomFieldAnswerStratigraphyViewModel) response.getAnswers().get(field);

        assertEquals(1, answer.getPosteriorRelationships().size());
    }
    @Test
    void initOrReuseResponse_addsAnteriorRelationships() {

        RecordingUnitDTO unit1 = createRecordingUnitDTO(1L);
        RecordingUnitDTO unit2 = createRecordingUnitDTO(2L);

        StratigraphicRelationshipDTO rel = createRelationship(unit2, unit1, true);

        unit1.getRelationshipsAsUnit2().add(rel);

        CustomField field = createStratigraphyField();
        FieldSource fs = fieldSourceWith(field);

        CustomFormResponseViewModel response =
                formService.initOrReuseResponse(null, unit1, fs, false);

        CustomFieldAnswerStratigraphyViewModel answer =
                (CustomFieldAnswerStratigraphyViewModel) response.getAnswers().get(field);

        assertEquals(1, answer.getAnteriorRelationships().size());
    }

    @Test
    void findCustomFormByRecordingUnitTypeAndInstitutionId_WithNullRecordingUnitType_ReturnsInstitutionForm() {
        // Arrange
        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(55L);

        CustomForm institutionForm = new CustomForm();
        given(formRepository.findEffectiveFormByTypeAndInstitution(null, 55L))
                .willReturn(Optional.of(institutionForm));

        // Act
        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(null, institutionDTO);

        // Assert
        assertNotNull(result);
        assertSame(institutionForm, result);
    }

    @Test
    void findCustomFormByRecordingUnitTypeAndInstitutionId_WithRecordingUnitType_ReturnsTypeSpecificForm() {
        // Arrange
        ConceptDTO recordingUnitTypeDTO = new ConceptDTO();
        recordingUnitTypeDTO.setId(101L);

        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(55L);

        CustomForm typeSpecificForm = new CustomForm();
        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.of(typeSpecificForm));

        // Act
        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitTypeDTO, institutionDTO);

        // Assert
        assertNotNull(result);
        assertSame(typeSpecificForm, result);
    }

    @Test
    void findCustomFormByRecordingUnitTypeAndInstitutionId_WithRecordingUnitType_FallsBackToInstitutionForm() {
        // Arrange
        ConceptDTO recordingUnitTypeDTO = new ConceptDTO();
        recordingUnitTypeDTO.setId(101L);

        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(55L);

        CustomForm institutionForm = new CustomForm();
        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.empty());
        given(formRepository.findEffectiveFormByTypeAndInstitution(null, 55L))
                .willReturn(Optional.of(institutionForm));

        // Act
        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitTypeDTO, institutionDTO);

        // Assert
        assertNotNull(result);
        assertSame(institutionForm, result);
    }

    @Test
    void findCustomFormByRecordingUnitTypeAndInstitutionId_ReturnsNullWhenNothingFound() {
        // Arrange
        ConceptDTO recordingUnitTypeDTO = new ConceptDTO();
        recordingUnitTypeDTO.setId(101L);

        InstitutionDTO institutionDTO = new InstitutionDTO();
        institutionDTO.setId(55L);

        given(formRepository.findEffectiveFormByTypeAndInstitution(101L, 55L))
                .willReturn(Optional.empty());
        given(formRepository.findEffectiveFormByTypeAndInstitution(null, 55L))
                .willReturn(Optional.empty());

        // Act
        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitTypeDTO, institutionDTO);

        // Assert
        assertNull(result);
    }

    @Test
    void findCustomFormByRecordingUnitTypeAndInstitutionId_WithNullInstitution_ReturnsNull() {
        // Arrange
        ConceptDTO recordingUnitTypeDTO = new ConceptDTO();
        recordingUnitTypeDTO.setId(101L);

        // Act
        CustomForm result = formService.findCustomFormByRecordingUnitTypeAndInstitutionId(recordingUnitTypeDTO,
                new InstitutionDTO());

        // Assert
        assertNull(result);
    }









}
