package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseAnswersProjectorTest {

    private final PhaseAnswersProjector projector = new PhaseAnswersProjector();

    private static String idOf(String binding) {
        return PhaseAnswersProjector.allFields().values().stream()
                .filter(f -> binding.equals(f.getValueBinding()))
                .map(f -> String.valueOf(f.getId()))
                .findFirst().orElseThrow();
    }

    private static ConceptDTO concept(long id) {
        ConceptDTO c = new ConceptDTO();
        c.setId(id);
        c.setExternalId("ext" + id);
        return c;
    }

    private static PhaseDTO phase(long id) {
        PhaseDTO dto = new PhaseDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    void fieldById_knowsTheCatalog() {
        String id = idOf("title");
        CustomField field = PhaseAnswersProjector.fieldById(id);
        assertThat(field).isNotNull();
        assertThat(PhaseAnswersProjector.fieldById("nope")).isNull();
    }

    @Test
    void resolveRequestedFieldIds_handlesAbsentAllAndListedIds() {
        assertThat(projector.resolveRequestedFieldIds(null)).isNull();
        assertThat(projector.resolveRequestedFieldIds(" ")).isNull();
        assertThat(projector.resolveRequestedFieldIds("ALL")).isEqualTo(PhaseAnswersProjector.allFields().keySet());
        assertThat(projector.resolveRequestedFieldIds(idOf("title") + ", 999999 ,")).containsExactly(idOf("title"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void project_readsTextIntegerAndConceptBindings() {
        PhaseDTO dto = phase(1L);
        dto.setTitle("Phase A");
        dto.setOrderNumber(3);
        dto.setType(concept(10));
        dto.setPeriods(Set.of(concept(20)));

        Map<Long, Map<String, Object>> out = projector.project(
                List.of(dto),
                Set.of(idOf("title"), idOf("orderNumber"), idOf("type"), idOf("periods")),
                Map.of(10L, "Type X", 20L, "Bronze"));

        Map<String, Object> answers = out.get(1L);
        assertThat(answers.get(idOf("title"))).isEqualTo("Phase A");
        assertThat(answers.get(idOf("orderNumber"))).isEqualTo(3);
        assertThat(answers.get(idOf("type"))).isEqualTo(new ResourceRef("10", "concepts", "Type X"));
        assertThat((List<Object>) answers.get(idOf("periods"))).containsExactly(new ResourceRef("20", "concepts", "Bronze"));
    }

    @Test
    void project_unresolvedLabelFallsBackToTheExternalId_andNullsStayNull() {
        PhaseDTO dto = phase(2L);
        dto.setType(concept(11));

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(idOf("type"), idOf("title")), null).get(2L);

        assertThat(answers.get(idOf("type"))).isEqualTo(new ResourceRef("11", "concepts", "[ext11]"));
        assertThat(answers.get(idOf("title"))).isNull();
    }

    @Test
    void project_skipsNullRowsRowsWithoutIdAndEmptySelections() {
        PhaseDTO noId = new PhaseDTO();
        assertThat(projector.project(null, Set.of(idOf("title")), Map.of())).isEmpty();
        assertThat(projector.project(List.of(), Set.of(idOf("title")), Map.of())).isEmpty();
        assertThat(projector.project(List.of(phase(1L)), null, Map.of())).isEmpty();
        assertThat(projector.project(java.util.Arrays.asList(null, noId), Set.of(idOf("title")), Map.of())).isEmpty();
    }

    @Test
    void relationAndUnknownBindingsProjectToNull() {
        Map<String, Object> answers = projector.projectOne(phase(3L), Map.of());
        assertThat(answers).containsKeys(PhaseAnswersProjector.allFields().keySet().toArray(new String[0]));
        assertThat(answers.get(idOf("recordingUnits"))).isNull();
    }

    @Test
    void projectOne_withoutIdIsEmpty() {
        assertThat(projector.projectOne(null, Map.of())).isEmpty();
        assertThat(projector.projectOne(new PhaseDTO(), Map.of())).isEmpty();
    }

    @Test
    void collectConcepts_gathersSingleAndCollectionConcepts() {
        PhaseDTO dto = phase(4L);
        dto.setType(concept(1));
        dto.setPeriods(Set.of(concept(2)));
        dto.setKeywords(Set.of(concept(3)));

        List<ConceptDTO> concepts = projector.collectConcepts(
                java.util.Arrays.asList(dto, null), Set.of(idOf("type"), idOf("periods"), idOf("keywords")));

        assertThat(concepts).extracting(ConceptDTO::getId).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(projector.collectConcepts(null, Set.of(idOf("type")))).isEmpty();
        assertThat(projector.collectConcepts(List.of(dto), Set.of())).isEmpty();
    }
}
