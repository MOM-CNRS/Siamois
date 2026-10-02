package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.dto.entity.DocumentDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAnswersProjectorTest {

    private final DocumentAnswersProjector projector = new DocumentAnswersProjector();

    private static String idOf(String binding) {
        return DocumentAnswersProjector.allFields().values().stream()
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

    private static DocumentDTO document(long id) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    void fieldById_knowsTheCatalog() {
        String id = idOf("title");
        CustomField field = DocumentAnswersProjector.fieldById(id);
        assertThat(field).isNotNull();
        assertThat(DocumentAnswersProjector.fieldById("nope")).isNull();
    }

    @Test
    void resolveRequestedFieldIds_handlesAbsentAllAndListedIds() {
        assertThat(projector.resolveRequestedFieldIds(null)).isNull();
        assertThat(projector.resolveRequestedFieldIds(" ")).isNull();
        assertThat(projector.resolveRequestedFieldIds("ALL")).isEqualTo(DocumentAnswersProjector.allFields().keySet());
        assertThat(projector.resolveRequestedFieldIds(idOf("title") + ", 999999 ,")).containsExactly(idOf("title"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void project_readsTextIntegerDecimalDateAndConceptBindings() {
        DocumentDTO dto = document(1L);
        dto.setTitle("Plan A");
        dto.setItemCount(3);
        dto.setSizeMb(2.5);
        dto.setProductionDate(java.time.OffsetDateTime.parse("2026-03-01T00:00:00Z"));
        dto.setCategory(concept(10));
        dto.setKeywords(Set.of(concept(20)));

        Map<Long, Map<String, Object>> out = projector.project(
                List.of(dto),
                Set.of(idOf("title"), idOf("itemCount"), idOf("sizeMb"), idOf("productionDate"), idOf("category"), idOf("keywords")),
                Map.of(10L, "Plan", 20L, "Bronze"));

        Map<String, Object> answers = out.get(1L);
        assertThat(answers).containsEntry(idOf("title"), "Plan A")
                .containsEntry(idOf("itemCount"), 3)
                .containsEntry(idOf("sizeMb"), 2.5)
                .containsEntry(idOf("productionDate"), java.time.OffsetDateTime.parse("2026-03-01T00:00:00Z"))
                .containsEntry(idOf("category"), new ResourceRef("10", "concepts", "Plan"));
        assertThat((List<Object>) answers.get(idOf("keywords"))).containsExactly(new ResourceRef("20", "concepts", "Bronze"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void project_readsPeopleAndTheLinkedEntitiesAsReferences() {
        DocumentDTO dto = document(5L);
        fr.siamois.dto.entity.PersonDTO author = new fr.siamois.dto.entity.PersonDTO();
        author.setId(9L);
        author.setName("Ada");
        author.setLastname("Lovelace");
        dto.setAuthors(List.of(author));
        fr.siamois.dto.entity.ActionUnitSummaryDTO project = new fr.siamois.dto.entity.ActionUnitSummaryDTO();
        project.setId(4L);
        project.setFullIdentifier("OA-4");
        dto.setActionUnit(project);
        fr.siamois.dto.entity.RecordingUnitSummaryDTO unit = new fr.siamois.dto.entity.RecordingUnitSummaryDTO();
        unit.setId(6L);
        unit.setFullIdentifier("UE-6");
        dto.setRecordingUnits(Set.of(unit));
        fr.siamois.dto.entity.SpecimenSummaryDTO find = new fr.siamois.dto.entity.SpecimenSummaryDTO();
        find.setId(7L);
        find.setFullIdentifier("M-7");
        dto.setFinds(Set.of(find));
        fr.siamois.dto.entity.SpatialUnitSummaryDTO place = new fr.siamois.dto.entity.SpatialUnitSummaryDTO();
        place.setId(8L);
        place.setName("Chantier");
        dto.setPlaces(Set.of(place));
        fr.siamois.dto.entity.PhaseDTO phase = new fr.siamois.dto.entity.PhaseDTO();
        phase.setId(11L);
        phase.setTitle("Bronze ancien");
        dto.setPhases(Set.of(phase));
        fr.siamois.dto.entity.ContainerDTO container = new fr.siamois.dto.entity.ContainerDTO();
        container.setId(12L);
        container.setIdentifier("C-12");
        dto.setContainers(Set.of(container));

        Map<String, Object> answers = projector.projectOne(dto, Map.of());

        assertThat(((List<ResourceRef>) answers.get(idOf("authors"))))
                .extracting(ResourceRef::resourceType).containsExactly("persons");
        assertThat(answers).containsEntry(idOf("actionUnit"), new ResourceRef("4", "action-units", "OA-4"));
        assertThat((List<ResourceRef>) answers.get(idOf("recordingUnits"))).containsExactly(new ResourceRef("6", "recording-units", "UE-6"));
        assertThat((List<ResourceRef>) answers.get(idOf("finds"))).containsExactly(new ResourceRef("7", "finds", "M-7"));
        assertThat((List<ResourceRef>) answers.get(idOf("places"))).containsExactly(new ResourceRef("8", "spatial-units", "Chantier"));
        assertThat((List<ResourceRef>) answers.get(idOf("phases"))).containsExactly(new ResourceRef("11", "phases", "Bronze ancien"));
        assertThat((List<ResourceRef>) answers.get(idOf("containers"))).containsExactly(new ResourceRef("12", "containers", "C-12"));
    }

    @Test
    void project_unresolvedLabelFallsBackToTheExternalId_andNullsStayNull() {
        DocumentDTO dto = document(2L);
        dto.setCategory(concept(11));

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(idOf("category"), idOf("title")), null).get(2L);

        assertThat(answers).containsEntry(idOf("category"), new ResourceRef("11", "concepts", "[ext11]"));
        assertThat(answers.get(idOf("title"))).isNull();
    }

    @Test
    void project_skipsNullRowsRowsWithoutIdAndEmptySelections() {
        DocumentDTO noId = new DocumentDTO();
        assertThat(projector.project(null, Set.of(idOf("title")), Map.of())).isEmpty();
        assertThat(projector.project(List.of(), Set.of(idOf("title")), Map.of())).isEmpty();
        assertThat(projector.project(List.of(document(1L)), null, Map.of())).isEmpty();
        assertThat(projector.project(java.util.Arrays.asList(null, noId), Set.of(idOf("title")), Map.of())).isEmpty();
    }

    @Test
    void everyFieldOfTheCatalogIsProjected_emptyOnesAsNullOrEmptyList() {
        Map<String, Object> answers = projector.projectOne(document(3L), Map.of());
        assertThat(answers).containsKeys(DocumentAnswersProjector.allFields().keySet().toArray(new String[0]));
        assertThat(answers.get(idOf("recordingUnits"))).isNull();
    }

    @Test
    void projectOne_withoutIdIsEmpty() {
        assertThat(projector.projectOne(null, Map.of())).isEmpty();
        assertThat(projector.projectOne(new DocumentDTO(), Map.of())).isEmpty();
    }

    @Test
    void collectConcepts_gathersSingleAndCollectionConcepts() {
        DocumentDTO dto = document(4L);
        dto.setCategory(concept(1));
        dto.setSupportNatures(Set.of(concept(2)));
        dto.setKeywords(Set.of(concept(3)));

        List<ConceptDTO> concepts = projector.collectConcepts(
                java.util.Arrays.asList(dto, null), Set.of(idOf("category"), idOf("supportNatures"), idOf("keywords")));

        assertThat(concepts).extracting(ConceptDTO::getId).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(projector.collectConcepts(null, Set.of(idOf("category")))).isEmpty();
        assertThat(projector.collectConcepts(List.of(dto), Set.of())).isEmpty();
    }
}
