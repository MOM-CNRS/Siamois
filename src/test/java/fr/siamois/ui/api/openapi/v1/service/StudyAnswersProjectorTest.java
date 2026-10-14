package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.StudyDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudyAnswersProjectorTest {

    private static final String IDENTIFIER_ID = "-801";
    private static final String METHOD_ID = "-804";
    private static final String DATE_ID = "-805";
    private static final String RECORDING_UNITS_ID = "-806";
    private static final String PLACE_ID = "-812";

    private final StudyAnswersProjector projector = new StudyAnswersProjector();

    private static ConceptDTO concept(long id) {
        ConceptDTO concept = new ConceptDTO();
        concept.setId(id);
        return concept;
    }

    private static StudyDTO study() {
        StudyDTO study = new StudyDTO();
        study.setId(1L);
        study.setIdentifier("ETU0001");
        study.setMethod(concept(9L));
        study.setStudyDate(OffsetDateTime.parse("2026-10-14T10:00:00Z"));
        study.setTreatmentPlace("Laboratoire");
        RecordingUnitSummaryDTO unit = new RecordingUnitSummaryDTO();
        unit.setId(5L);
        study.setRecordingUnits(Set.of(unit));
        return study;
    }

    @Test
    void fieldById_findsTheSystemFieldsOfTheStudyTable() {
        CustomField field = StudyAnswersProjector.fieldById(METHOD_ID);

        assertThat(field).isNotNull();
        assertThat(field.getValueBinding()).isEqualTo("method");
        assertThat(StudyAnswersProjector.fieldById("nope")).isNull();
    }

    @Test
    void resolveRequestedFieldIds_acceptsAllOrAKnownSubset() {
        assertThat(projector.resolveRequestedFieldIds(null)).isNull();
        assertThat(projector.resolveRequestedFieldIds(" ")).isNull();
        assertThat(projector.resolveRequestedFieldIds("all")).contains(IDENTIFIER_ID, METHOD_ID, PLACE_ID);
        assertThat(projector.resolveRequestedFieldIds(METHOD_ID + ", nope," + DATE_ID)).containsExactly(METHOD_ID, DATE_ID);
    }

    @Test
    void collectConcepts_gathersTheConceptsOfTheRequestedFields() {
        StudyDTO study = study();

        assertThat(projector.collectConcepts(List.of(study), Set.of(METHOD_ID))).containsExactly(study.getMethod());
        assertThat(projector.collectConcepts(List.of(study), Set.of(DATE_ID))).isEmpty();
        assertThat(projector.collectConcepts(null, Set.of(METHOD_ID))).isEmpty();
        assertThat(projector.collectConcepts(List.of(study), null)).isEmpty();
    }

    @Test
    void project_shapesTextConceptDateAndLinkValues() {
        StudyDTO study = study();

        Map<Long, Map<String, Object>> projected = projector.project(List.of(study),
                Set.of(IDENTIFIER_ID, METHOD_ID, DATE_ID, PLACE_ID, RECORDING_UNITS_ID), Map.of(9L, "Microscopie"));

        Map<String, Object> answers = projected.get(1L);
        assertThat(answers.get(IDENTIFIER_ID)).isEqualTo("ETU0001");
        assertThat(answers.get(PLACE_ID)).isEqualTo("Laboratoire");
        assertThat(answers.get(DATE_ID)).isEqualTo(study.getStudyDate());
        assertThat(answers.get(METHOD_ID)).isInstanceOfSatisfying(ResourceRef.class, ref -> {
            assertThat(ref.resourceId()).isEqualTo("9");
            assertThat(ref.label()).isEqualTo("Microscopie");
        });
        // the links are references, whose list MultiValueAnswers cuts afterwards
        assertThat(answers.get(RECORDING_UNITS_ID)).isInstanceOf(List.class);
        List<?> links = (List<?>) answers.get(RECORDING_UNITS_ID);
        assertThat(links).hasSize(1);
        ResourceRef link = (ResourceRef) links.get(0);
        assertThat(link.resourceId()).isEqualTo("5");
        assertThat(link.resourceType()).isEqualTo("recording-units");
    }

    @Test
    void project_skipsRowsWithoutIdAndEmptySelections() {
        assertThat(projector.project(List.of(new StudyDTO()), Set.of(METHOD_ID), Map.of())).isEmpty();
        assertThat(projector.project(List.of(study()), Set.of("nope"), Map.of())).isEmpty();
        assertThat(projector.project(null, Set.of(METHOD_ID), Map.of())).isEmpty();
    }

    @Test
    void projectOne_projectsEveryFieldOfTheDetail() {
        StudyDTO study = study();
        ActionUnitSummaryDTO project = new ActionUnitSummaryDTO();
        project.setId(3L);
        project.setFullIdentifier("PRJ-3");
        study.setActionUnit(project);

        Map<String, Object> answers = projector.projectOne(study, Map.of(9L, "Microscopie"));

        assertThat(answers).containsKeys(IDENTIFIER_ID, METHOD_ID, DATE_ID, PLACE_ID);
        assertThat(answers.get("-802")).isInstanceOfSatisfying(ResourceRef.class,
                ref -> assertThat(ref.label()).isEqualTo("PRJ-3"));
        assertThat(projector.projectOne(null, Map.of())).isEmpty();
        assertThat(projector.projectOne(new StudyDTO(), Map.of())).isEmpty();
    }
}
