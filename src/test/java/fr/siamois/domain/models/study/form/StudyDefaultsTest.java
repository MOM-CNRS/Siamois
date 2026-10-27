package fr.siamois.domain.models.study.form;

import fr.siamois.domain.models.form.config.SystemFieldSpec;
import fr.siamois.domain.models.form.customfield.CustomField;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StudyDefaultsTest {

    private static Set<String> bindingsOfStudyForm() {
        return StudyForm.systemFields().stream()
                .map(SystemFieldSpec::field)
                .map(CustomField::getValueBinding)
                .collect(Collectors.toSet());
    }

    @Test
    void everyDefaultBindingIsAFieldOfTheStudyForm() {
        Set<String> declared = bindingsOfStudyForm();

        assertTrue(declared.containsAll(StudyDefaults.ALL_BINDINGS));
    }

    @Test
    void systemFieldIdsAreUniqueAndInTheStudyRange() {
        List<Long> ids = StudyForm.systemFields().stream().map(spec -> spec.field().getId()).toList();

        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertTrue(ids.stream().allMatch(id -> id <= -801L && id >= -830L));
    }

    @Test
    void studiesOfFindsAndOfRecordingUnitsUseTheTwoLinksAndNoTreatmentColumn() {
        for (StudyDefaults.DefaultStudyType type : List.of(StudyDefaults.FIND_STUDY, StudyDefaults.RECORDING_UNIT_STUDY)) {
            assertTrue(type.isActive("recordingUnits"));
            assertTrue(type.isActive("specimens"));
            StudyForm.TREATMENT_BINDINGS.forEach(binding -> assertFalse(type.isActive(binding), binding));
        }
    }

    @Test
    void theTreatmentUsesTheNineColumnsOfTheSdaSheet() {
        StudyDefaults.DefaultStudyType treatment = StudyDefaults.TREATMENT;

        // the treated find stands for code_element_traite; code_OA_NAT comes from the project
        assertTrue(treatment.isActive("specimens"));
        assertFalse(treatment.isActive("recordingUnits"));
        StudyForm.TREATMENT_BINDINGS.forEach(binding -> assertTrue(treatment.isActive(binding), binding));
        // the reference's mandatory columns
        for (String mandatory : List.of("specimens", "treatmentType", "reportCode", "treatmentStart", "treatmentPlace")) {
            assertTrue(treatment.isMandatory(mandatory), mandatory);
        }
        // and its optional ones
        for (String optional : List.of("treatmentManager", "treatmentIdentifier", "treatmentComments", "treatmentEnd")) {
            assertFalse(treatment.isMandatory(optional), optional);
        }
    }

    @Test
    void mandatoryFieldsAreActive() {
        for (StudyDefaults.DefaultStudyType type : StudyDefaults.TYPES) {
            assertTrue(type.active().containsAll(type.mandatory()), type.key());
        }
    }

    @Test
    void theThreeTypesHaveDistinctKeys() {
        assertEquals(3, StudyDefaults.TYPES.stream().map(StudyDefaults.DefaultStudyType::key).distinct().count());
    }
}
