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
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.form.CustomFormRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for managing custom form responses.
 * This service handles the creation, updating, and deletion of custom field answers
 * associated with a custom form response.
 */
@Service
public class CustomFormResponseService {

    private static final String UNSUPPORTED_FIELD_TYPE_MESSAGE = "Unsupported field type";

    private final CustomFormRepository customFormRepository;

    public CustomFormResponseService(CustomFormRepository customFormRepository) {
        this.customFormRepository = customFormRepository;
    }

    private CustomFieldAnswer createAnswer(CustomFieldAnswerId pk, CustomFieldAnswer answer) {
        CustomFieldAnswer createdAnswer;
        if (pk.getField() instanceof CustomFieldInteger) {
            createdAnswer = new CustomFieldAnswerInteger();
            ((CustomFieldAnswerInteger) createdAnswer).setValue(((CustomFieldAnswerInteger) answer).getValue());
        } else if (pk.getField() instanceof CustomFieldSelectMultiple) {
            createdAnswer = new CustomFieldAnswerSelectMultiple();
            ((CustomFieldAnswerSelectMultiple) createdAnswer).setValue(((CustomFieldAnswerSelectMultiple) answer).getValue());
        } else {
            throw new IllegalArgumentException(UNSUPPORTED_FIELD_TYPE_MESSAGE);
        }
        createdAnswer.setPk(pk);
        return createdAnswer;
    }

    private boolean hasValueChanged(CustomField field, CustomFieldAnswer existingAnswer, CustomFieldAnswer newValue) {
        if (field instanceof CustomFieldInteger) {
            return !Objects.equals(((CustomFieldAnswerInteger) existingAnswer).getValue(),
                    ((CustomFieldAnswerInteger) newValue).getValue());
        } else if (field instanceof CustomFieldSelectMultiple) {
            // Checking if the answer contains the same concepts
            return !Objects.equals(
                    new HashSet<>(((CustomFieldAnswerSelectMultiple) existingAnswer).getValue()),
                    new HashSet<>(((CustomFieldAnswerSelectMultiple) newValue).getValue())
            );
        } else {
            throw new IllegalArgumentException(UNSUPPORTED_FIELD_TYPE_MESSAGE);
        }
    }

    private void updateAnswer(CustomFieldAnswer managedAnswer, CustomFieldAnswer answer, CustomFieldAnswerId pk) {
        if (managedAnswer.getPk().getField() instanceof CustomFieldInteger) {
            ((CustomFieldAnswerInteger) managedAnswer).setValue(((CustomFieldAnswerInteger) answer).getValue());
        } else if (managedAnswer.getPk().getField() instanceof CustomFieldSelectMultiple) {
            CustomFieldAnswerSelectMultiple managedMultiAnswer = (CustomFieldAnswerSelectMultiple) managedAnswer;
            CustomFieldAnswerSelectMultiple newMultiAnswer = (CustomFieldAnswerSelectMultiple) answer;

            // Get the concepts from both answers
            Set<Concept> existingConcepts = new HashSet<>(managedMultiAnswer.getValue());
            Set<Concept> newConcepts = new HashSet<>(newMultiAnswer.getValue());

            // Find concepts to remove (in existing but not in new)
            Set<Concept> conceptsToRemove = new HashSet<>(existingConcepts);
            conceptsToRemove.removeAll(newConcepts);

            // Find concepts to add (in new but not in existing)
            Set<Concept> conceptsToAdd = new HashSet<>(newConcepts);
            conceptsToAdd.removeAll(existingConcepts);

            // Remove concepts that are no longer selected
            for (Concept concept : conceptsToRemove) {
                managedMultiAnswer.removeConcept(concept);
            }

            // Add newly selected concepts
            for (Concept concept : conceptsToAdd) {
                managedMultiAnswer.addConcept(concept);
            }
        } else {
            throw new IllegalArgumentException(UNSUPPORTED_FIELD_TYPE_MESSAGE);
        }
        managedAnswer.setPk(pk);
    }

    private void saveAnswer(CustomField managedField,
                            CustomFormResponse customFormResponse,
                            CustomFormResponse managedFormResponse,
                            Map<CustomField, CustomFieldAnswer> toBeDeleted
    ) {
        CustomFieldAnswer answer = customFormResponse.getAnswers().get(managedField); // get answer

        if (answer != null) {

            CustomFieldAnswerId pk = new CustomFieldAnswerId();
            pk.setFormResponse(managedFormResponse);
            pk.setField(managedField);

            // Get the answer if it already exists and modify it if necessary. Otherwise, create it and add it.
            CustomFieldAnswer managedAnswer;
            if (managedFormResponse.getAnswers().containsKey(managedField)) {
                managedAnswer = managedFormResponse.getAnswers().get(managedField);
                // Update if necessary
                if (hasValueChanged(pk.getField(), managedAnswer, answer)) {
                    updateAnswer(managedAnswer, answer, pk);
                }
            } else {
                // Create
                managedAnswer = createAnswer(pk, answer);
                managedFormResponse.addAnswer(managedAnswer);
            }

            // In both case, remove the answer from the list of answzr to be removed
            toBeDeleted.remove(managedField);

        }
    }

    /**
     * Process the form response and its answers. By removing answers that are not part of the form.
     *
     * @param managedFormResponse the managed form response that is saved
     * @param customFormResponse  the custom form response that is being saved
     */
    public void saveFormResponse(CustomFormResponse managedFormResponse, CustomFormResponse customFormResponse) {

        // get form
        CustomForm managedForm;
        Map<CustomField, CustomFieldAnswer> toBeDeleted;

        Optional<CustomForm> optManagedForm = customFormRepository.findById(customFormResponse.getForm().getId());
        if (optManagedForm.isPresent()) {
            managedForm = optManagedForm.get();
        } else {
            throw new IllegalArgumentException("CustomForm not found");
        }
        managedFormResponse.setForm(managedForm);

        // Create a copy of the saved answer to keep track of the ones to delete
        toBeDeleted = new HashMap<>(managedFormResponse.getAnswers());

        // Iterate over the form fields and look for answers to fields that are in the form

        managedForm.getLayout().stream()
                .flatMap(section -> section.getRows().stream()) // Stream rows in each section
                .flatMap(row -> row.getColumns().stream())      // Stream columns in each row
                .map(CustomCol::getField)               // Extract the field from each column
                .forEach(field -> saveAnswer(field,
                        customFormResponse,
                        managedFormResponse,
                        toBeDeleted));     // Process each field

        // Delete the answer to be deleted
        for (CustomFieldAnswer managedAnswerToDelete : toBeDeleted.values()) {

            if (managedAnswerToDelete != null) {
                // Clear associations between the answer and the concept list if the type is select multiple
                if (managedAnswerToDelete instanceof CustomFieldAnswerSelectMultiple managedCustomFieldAnswerSelectMultiple) {
                    managedCustomFieldAnswerSelectMultiple.getValue().clear();
                }

                // Remove the answer to trigger its deletion
                managedFormResponse.getAnswers().remove(managedAnswerToDelete.getPk().getField());

            }
        }

    }
}
