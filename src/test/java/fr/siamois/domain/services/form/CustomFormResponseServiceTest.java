package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectMultiple;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerId;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerInteger;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerSelectMultiple;
import fr.siamois.domain.models.form.customform.CustomCol;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.form.customform.CustomFormPanel;
import fr.siamois.domain.models.form.customform.CustomRow;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.form.CustomFormRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFormResponseServiceTest {

    @Mock
    private CustomFormRepository customFormRepository;

    @InjectMocks
    private CustomFormResponseService customFormResponseService;

    // Locals
    CustomFormResponse customFormResponse; // The new form response to be submitted
    CustomFormResponse managedFormResponse; // the managed instance retrieved from DB and to be modified based on submision
    Map<CustomField, CustomFieldAnswer> toBeDeleted; // Field answers to be deleted
    CustomField managedField; // The field to process
    CustomForm form; // A form
    CustomFieldSelectMultiple field1SelectMultiple;
    CustomFieldSelectMultiple field2SelectMultiple;
    CustomFieldSelectMultiple field3SelectMultiple;
    CustomFieldSelectMultiple field4SelectMultipleToBeDeleted;
    CustomFieldInteger field1Integer;
    CustomFieldInteger field2Integer;
    CustomFieldInteger field3Integer;
    Concept field1Concept;
    Concept field2Concept;
    Concept field3Concept;
    Concept field4Concept;
    Concept field5Concept;
    Concept field6Concept;
    Concept field7ConceptToBeDelete;
    Concept answer1Value ;
    Concept answer2ValueOld ;
    Concept answer2ValueNew;
    Concept answer3Value;
    Concept answer4ValueToBeDeleted;
    CustomFieldAnswerSelectMultiple a7ToBeDeleted = new CustomFieldAnswerSelectMultiple();

    @BeforeEach
    void setUp() {
        // Concepts
        field1Concept = new Concept();
        field1Concept.setExternalId("q1");
        field2Concept = new Concept();
        field2Concept.setExternalId("q2");
        field3Concept = new Concept();
        field3Concept.setExternalId("q3");
        field4Concept = new Concept();
        field4Concept.setExternalId("q4");
        field5Concept = new Concept();
        field5Concept.setExternalId("q5");
        field6Concept = new Concept();
        field6Concept.setExternalId("q6");
        field7ConceptToBeDelete = new Concept();
        field1Concept.setExternalId("q7");
        answer1Value = new Concept();
        answer2ValueOld = new Concept();
        answer2ValueNew = new Concept();
        answer3Value = new Concept();
        answer1Value.setExternalId("a1");
        answer2ValueOld.setExternalId("a2_old");
        answer2ValueNew.setExternalId("a2_new");
        answer3Value.setExternalId("a3");
        answer4ValueToBeDeleted = new Concept();
        answer4ValueToBeDeleted.setExternalId("a4");
        // The fields we are going to test
        field1SelectMultiple = new CustomFieldSelectMultiple();
        field1SelectMultiple.setLabel("Select Multiple");
        field1SelectMultiple.setConcept(field1Concept);
        field2SelectMultiple = new CustomFieldSelectMultiple();
        field2SelectMultiple.setLabel("Select Multiple");
        field2SelectMultiple.setConcept(field2Concept);
        field3SelectMultiple = new CustomFieldSelectMultiple();
        field3SelectMultiple.setLabel("Select Multiple");
        field3SelectMultiple.setConcept(field3Concept);
        field4SelectMultipleToBeDeleted = new CustomFieldSelectMultiple();
        field4SelectMultipleToBeDeleted.setLabel("Select Multiple");
        field4SelectMultipleToBeDeleted.setConcept(field7ConceptToBeDelete);
        field1Integer = new CustomFieldInteger();
        field1Integer.setLabel("Integer");
        field1Integer.setConcept(field4Concept);
        field2Integer = new CustomFieldInteger();
        field2Integer.setLabel("Integer");
        field2Integer.setConcept(field5Concept);
        field3Integer = new CustomFieldInteger();
        field3Integer.setLabel("Integer");
        field3Integer.setConcept(field6Concept);

        // ------------ Define the form
        CustomFormPanel customFormPanel = new CustomFormPanel();
        CustomRow customRow = new CustomRow();
        customFormPanel.setRows(List.of(customRow));
        customRow.setColumns(new ArrayList<>());
        customRow.getColumns().add(new CustomCol());
        customRow.getColumns().get(0).setField(field1Integer);
        customRow.getColumns().add(new CustomCol());
        customRow.getColumns().get(1).setField(field2Integer);
        customRow.getColumns().add(new CustomCol());
        customRow.getColumns().get(2).setField(field3Integer);
        customRow.getColumns().add(new CustomCol());
        customRow.getColumns().get(3).setField(field1SelectMultiple);
        customRow.getColumns().add(new CustomCol());
        customRow.getColumns().get(4).setField(field2SelectMultiple);
        customRow.getColumns().add(new CustomCol());
        customRow.getColumns().get(5).setField(field3SelectMultiple);


        // we don't add the form 7 to the field bc we want to remove it
        form = new CustomForm();
        form.setLayout(new ArrayList<>());
        form.setId(-1L);
        form.getLayout().add(customFormPanel);

        // Prepare the answers
        // Two answers won't be modified bc they did not change
        CustomFieldAnswerSelectMultiple a1 = new CustomFieldAnswerSelectMultiple();
        CustomFieldAnswerId pk1 = new CustomFieldAnswerId();
        pk1.setField(field1SelectMultiple);
        a1.setPk(pk1);
        a1.setValue(new ArrayList<>());
        a1.getValue().add(answer1Value);
        CustomFieldAnswerInteger a2 = new CustomFieldAnswerInteger();
        CustomFieldAnswerId pk2 = new CustomFieldAnswerId();
        pk2.setField(field1Integer);
        a2.setPk(pk2);
        a2.setValue(2);
        // Two answers will be updated because the value did change
        CustomFieldAnswerSelectMultiple a3Old = new CustomFieldAnswerSelectMultiple();
        CustomFieldAnswerId pk3 = new CustomFieldAnswerId();
        pk3.setField(field2SelectMultiple);
        a3Old.setPk(pk3);
        a3Old.setValue(new ArrayList<>());
        a3Old.getValue().add(answer2ValueOld);
        CustomFieldAnswerSelectMultiple a3new = new CustomFieldAnswerSelectMultiple();
        pk3.setField(field2SelectMultiple);
        a3new.setPk(pk3);
        a3new.setValue(new ArrayList<>());
        a3new.getValue().add(answer2ValueNew);
        CustomFieldAnswerInteger a4old = new CustomFieldAnswerInteger();
        CustomFieldAnswerId pk4 = new CustomFieldAnswerId();
        pk4.setField(field2Integer);
        a4old.setPk(pk4);
        a4old.setValue(3);
        CustomFieldAnswerInteger a4new = new CustomFieldAnswerInteger();
        pk4.setField(field2Integer);
        a4new.setPk(pk4);
        a4new.setValue(4);
        // Two answers will be created
        CustomFieldAnswerSelectMultiple a5 = new CustomFieldAnswerSelectMultiple();
        CustomFieldAnswerId pk5 = new CustomFieldAnswerId();
        pk5.setField(field3SelectMultiple);
        a5.setPk(pk5);
        a5.setValue(new ArrayList<>());
        a5.getValue().add(answer3Value);
        CustomFieldAnswerInteger a6 = new CustomFieldAnswerInteger();
        CustomFieldAnswerId pk6 = new CustomFieldAnswerId();
        pk6.setField(field1Integer);
        a6.setPk(pk6);
        a6.setValue(6);
        // One answer to be deleted

        CustomFieldAnswerId pk7 = new CustomFieldAnswerId();
        pk7.setField(field4SelectMultipleToBeDeleted);
        a7ToBeDeleted.setPk(pk7);
        a7ToBeDeleted.setValue(new ArrayList<>());
        a7ToBeDeleted.getValue().add(answer4ValueToBeDeleted);


        // Prepare the form response to be modified
        managedFormResponse = new CustomFormResponse();
        managedFormResponse.setForm(form);
        managedFormResponse.setAnswers(new HashMap<>());
        managedFormResponse.getAnswers().put(field1SelectMultiple,a1);
        managedFormResponse.getAnswers().put(field1Integer,a2);
        managedFormResponse.getAnswers().put(field2SelectMultiple,a3Old);
        managedFormResponse.getAnswers().put(field2Integer,a4old);
        managedFormResponse.getAnswers().put(field4SelectMultipleToBeDeleted,a7ToBeDeleted);
        // -------------

        // Prepare the form response to be submitted
        customFormResponse = new CustomFormResponse();
        customFormResponse.setForm(form);
        customFormResponse.setAnswers(new HashMap<>());
        customFormResponse.getAnswers().put(field1SelectMultiple,a1);
        customFormResponse.getAnswers().put(field1Integer,a2);
        customFormResponse.getAnswers().put(field2SelectMultiple,a3new);
        customFormResponse.getAnswers().put(field2Integer,a4new);
        customFormResponse.getAnswers().put(field3SelectMultiple,a5);
        customFormResponse.getAnswers().put(field3Integer,a6);
        // -------------

        // Init the list with the answers to be deleted
        toBeDeleted = new HashMap<>(managedFormResponse.getAnswers());

    }



    @Test
    void saveFormResponse_success() {
        //mock

        when(customFormRepository.findById(anyLong()))
                .thenReturn(Optional.of(form));


        //act
        customFormResponseService.saveFormResponse(
                managedFormResponse,
                customFormResponse
        );


        CustomFieldAnswerInteger answer1 =
                (CustomFieldAnswerInteger) managedFormResponse
                        .getAnswers().get(field3Integer);

        // Assess that the value is unchanged
        assertEquals( 6,answer1.getValue());

        CustomFieldAnswerInteger answer2 =
                (CustomFieldAnswerInteger) managedFormResponse
                        .getAnswers().get(field2Integer);

        // Assess that the value is unchanged
        assertEquals( 4,answer2.getValue());

        CustomFieldAnswerInteger answer3 =
                (CustomFieldAnswerInteger) managedFormResponse
                        .getAnswers().get(field1Integer);

        // Assess that the value is unchanged
        assertEquals( 2,answer3.getValue());

        CustomFieldAnswerSelectMultiple answer4 = (CustomFieldAnswerSelectMultiple)
                managedFormResponse.getAnswers().get(field3SelectMultiple);


        assertEquals( 1,answer4.getValue().size());
        // Assess that value is unchanged
        assertTrue(answer4.getValue().contains(answer3Value));

        CustomFieldAnswerSelectMultiple answer5 = (CustomFieldAnswerSelectMultiple)
                managedFormResponse.getAnswers().get(field2SelectMultiple);


        assertEquals( 1,answer5.getValue().size());
        // Assess that value is unchanged
        assertTrue(answer5.getValue().contains(answer2ValueNew));

        CustomFieldAnswerSelectMultiple answer6 = (CustomFieldAnswerSelectMultiple)
                managedFormResponse.getAnswers().get(field1SelectMultiple);

        // Assess that the answer in the managedformresponse is unchanged
        assertEquals( 1,answer6.getValue().size());
        // Assess that value is unchanged
        assertTrue(answer6.getValue().contains(answer1Value));

        // assess there is only 6 responses
        assertEquals(6, managedFormResponse.getAnswers().size());

        // assess the value of the answer to be deleted has been removed
        assertEquals(0, a7ToBeDeleted.getValue().size());

    }
}