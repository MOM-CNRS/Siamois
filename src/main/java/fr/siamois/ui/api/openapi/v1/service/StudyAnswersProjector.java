package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.domain.models.study.Study;
import fr.siamois.dto.entity.StudyDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Projects a study's form fields into a flat {@code answers} map: all of them are system fields bound to a
 * property of {@link StudyDTO}. The links to recording units and finds are loaded whole, like a document's,
 * and {@link MultiValueAnswers} then cuts each list to the requested number of values.
 */
@Component
public class StudyAnswersProjector extends ReflectiveAnswersProjector<StudyDTO> {

    private static final Map<String, CustomField> FIELDS_BY_ID = indexFields(ConfigurableTable.STUDY);

    public StudyAnswersProjector() {
        super(StudyDTO.class, Study.class, FIELDS_BY_ID);
    }

    public static CustomField fieldById(String fieldId) {
        return FIELDS_BY_ID.get(fieldId);
    }
}
