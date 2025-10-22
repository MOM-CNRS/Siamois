package fr.siamois.domain.services.form;

import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.repositories.form.FormRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing custom forms.
 */
@Service
public class FormService {

    private final FormRepository formRepository;

    public FormService(FormRepository formRepository) {
        this.formRepository = formRepository;
    }

    /**
     * Find a form by its ID
     *
     * @param id The ID of the form
     * @return The form having the given ID
     */
    @Transactional(readOnly = true)
    public CustomForm findById(long id) {
        return formRepository.findById(id).orElse(null);
    }

    /**
     * Find the form to display for a given type of recording unit in the context of an institution
     *
     * @param recordingUnitType The type of recording unit
     * @param institution The institution
     * @return The form
     */
    @Transactional(readOnly = true)
    public CustomForm findCustomFormByRecordingUnitTypeAndInstitutionId(Concept recordingUnitType, Institution institution) {
        // Should we throw an error if none found?
        return formRepository.findEffectiveFormByTypeAndInstitution(recordingUnitType.getId(), institution.getId()).orElse(null);
    }

}
