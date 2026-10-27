package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldBoolean;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldDecimal;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldInteger;
import fr.siamois.domain.models.form.customfield.basetypes.CustomFieldText;
import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldSelectOneFromFieldCode;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.dto.entity.*;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared reflective projection, on a probe DTO that exercises what no real table's form does. */
class ReflectiveAnswersProjectorTest {

    /** A DTO whose getters cover the value kinds and the failures a projection must survive. */
    public static class ProbeDTO extends AbstractEntityDTO {
        private Object value;
        private Object other;

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public Object getOther() {
            return other;
        }

        public void setOther(Object other) {
            this.other = other;
        }

        public Object getExploding() {
            throw new IllegalStateException("boom");
        }
    }

    private static final class ProbeProjector extends ReflectiveAnswersProjector<ProbeDTO> {
        ProbeProjector(Map<String, CustomField> fields) {
            super(ProbeDTO.class, RecordingUnit.class, fields);
        }
    }

    private static CustomField text(long id, String binding) {
        return CustomFieldText.builder().id(id).valueBinding(binding).build();
    }

    private static ProbeDTO probe(Object value) {
        ProbeDTO dto = new ProbeDTO();
        dto.setId(1L);
        dto.setValue(value);
        return dto;
    }

    private static ProbeProjector projectorOf(CustomField... fields) {
        Map<String, CustomField> byId = new LinkedHashMap<>();
        for (CustomField field : fields) byId.put(String.valueOf(field.getId()), field);
        return new ProbeProjector(byId);
    }

    private static Object answerOf(ProbeProjector projector, ProbeDTO dto, CustomField field) {
        return projector.project(List.of(dto), Set.of(String.valueOf(field.getId())), Map.of()).get(1L)
                .get(String.valueOf(field.getId()));
    }

    @Test
    void aBindingWithoutPropertyOrWithoutBindingProjectsNothing() {
        CustomField ghost = text(1L, "ghost");
        CustomField unbound = text(2L, " ");
        ProbeProjector projector = projectorOf(ghost, unbound);

        assertThat(answerOf(projector, probe("x"), ghost)).isNull();
        assertThat(answerOf(projector, probe("x"), unbound)).isNull();
    }

    @Test
    void aGetterThatFailsProjectsNothing() {
        CustomField exploding = text(1L, "exploding");

        assertThat(answerOf(projectorOf(exploding), probe("x"), exploding)).isNull();
    }

    @Test
    void aRelationFieldIsNeverReadFromTheDto() {
        CustomField parents = text(1L, "parents");
        ProbeDTO dto = probe("x");

        assertThat(answerOf(projectorOf(parents), dto, parents)).isNull();
    }

    @Test
    void numbersAndBooleansOfTheWrongKindProjectNothing() {
        CustomField integer = CustomFieldInteger.builder().id(1L).valueBinding("value").build();
        CustomField decimal = CustomFieldDecimal.builder().id(2L).valueBinding("value").build();
        CustomField bool = CustomFieldBoolean.builder().id(3L).valueBinding("value").build();
        ProbeProjector projector = projectorOf(integer, decimal, bool);

        assertThat(answerOf(projector, probe(12L), integer)).isEqualTo(12);
        assertThat(answerOf(projector, probe("twelve"), integer)).isNull();
        assertThat(answerOf(projector, probe(1.5d), decimal)).isEqualTo(1.5d);
        assertThat(answerOf(projector, probe("1.5"), decimal)).isNull();
        assertThat(answerOf(projector, probe(true), bool)).isEqualTo(true);
        assertThat(answerOf(projector, probe("yes"), bool)).isNull();
    }

    @Test
    void aReferenceOfAnUnknownKindProjectsNothing() {
        CustomField select = CustomFieldSelectOneFromFieldCode.builder().id(1L).valueBinding("value").build();

        assertThat(answerOf(projectorOf(select), probe("a plain string"), select)).isNull();
    }

    @Test
    void referencesAreLabelledByTheirOwnKind() {
        CustomField select = CustomFieldSelectOneFromFieldCode.builder().id(1L).valueBinding("value").build();
        ProbeProjector projector = projectorOf(select);

        PersonDTO person = new PersonDTO();
        person.setId(2L);
        person.setName("Ada");
        person.setLastname("Lovelace");
        ActionUnitSummaryDTO namedProject = new ActionUnitSummaryDTO();
        namedProject.setId(3L);
        namedProject.setName("Chantier");
        SpatialUnitSummaryDTO place = new SpatialUnitSummaryDTO();
        place.setId(4L);
        place.setName("Lyon");
        SpecimenSummaryDTO find = new SpecimenSummaryDTO();
        find.setId(5L);
        find.setFullIdentifier("M-5");
        ContainerDTO container = new ContainerDTO();
        container.setId(6L);
        container.setIdentifier("C-6");
        PhaseDTO titled = new PhaseDTO();
        titled.setId(7L);
        titled.setTitle("Occupation");
        PhaseDTO untitled = new PhaseDTO();
        untitled.setId(8L);
        untitled.setTitle(" ");
        untitled.setIdentifier("PH-8");

        List<?> refs = (List<?>) answerOf(projector,
                probe(List.of(person, namedProject, place, find, container, titled, untitled)), select);

        assertThat(refs).extracting(ref -> ((ResourceRef) ref).resourceType())
                .containsExactly("persons", "projects", "places", "finds", "containers", "phases", "phases");
        assertThat(refs).extracting(ref -> ((ResourceRef) ref).label())
                .contains("Chantier", "Lyon", "M-5", "C-6", "Occupation", "PH-8");
    }

    @Test
    void collectConceptsReadsConceptsFromCollectionsToo() {
        CustomField select = CustomFieldSelectOneFromFieldCode.builder().id(1L).valueBinding("value").build();
        fr.siamois.dto.entity.vocabulary.ConceptDTO concept = new fr.siamois.dto.entity.vocabulary.ConceptDTO();
        concept.setId(9L);

        assertThat(projectorOf(select).collectConcepts(List.of(probe(List.of(concept, "not a concept"))), Set.of("1")))
                .containsExactly(concept);
    }
}
