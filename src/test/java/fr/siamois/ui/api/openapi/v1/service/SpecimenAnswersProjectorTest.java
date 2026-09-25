package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpecimenAnswersProjectorTest {

    private final SpecimenAnswersProjector projector = new SpecimenAnswersProjector();

    private static CustomField fieldFor(String valueBinding) {
        return SystemFieldCatalog.fieldsOf(ConfigurableTable.MOBILIER).stream()
                .filter(f -> valueBinding.equals(f.getValueBinding()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No find field bound to '" + valueBinding + "'"));
    }

    private static String fieldId(String valueBinding) {
        return String.valueOf(fieldFor(valueBinding).getId());
    }

    private static ConceptDTO concept(long id) {
        ConceptDTO c = new ConceptDTO();
        c.setId(id);
        c.setExternalId("c" + id);
        return c;
    }

    private static SpecimenDTO find(long id) {
        SpecimenDTO dto = new SpecimenDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    void resolveRequestedFieldIds_absentParam_meansNoProjection() {
        assertThat(projector.resolveRequestedFieldIds(null)).isNull();
        assertThat(projector.resolveRequestedFieldIds(" ")).isNull();
    }

    @Test
    void resolveRequestedFieldIds_allCoversTheDetailsForm_andUnknownIdsAreIgnored() {
        assertThat(projector.resolveRequestedFieldIds("all")).contains(fieldId("category"), fieldId("material"), fieldId("taq"));
        assertThat(projector.resolveRequestedFieldIds(fieldId("taq") + ",999999,")).containsExactly(fieldId("taq"));
    }

    @Test
    void project_readsScalarsAndReferences() {
        SpecimenDTO dto = find(1L);
        dto.setDescription("Tesson");
        dto.setTaq(1200);
        dto.setCategory(concept(5L));
        RecordingUnitSummaryDTO ru = new RecordingUnitSummaryDTO();
        ru.setId(42L);
        ru.setFullIdentifier("INST-P-UE42");
        dto.setRecordingUnit(ru);
        PersonDTO author = new PersonDTO();
        author.setId(3L);
        dto.setAuthors(List.of(author));

        Set<String> ids = new LinkedHashSet<>(List.of(fieldId("description"), fieldId("taq"), fieldId("category"),
                fieldId("recordingUnit"), fieldId("authors")));
        Map<String, Object> answers = projector.project(List.of(dto), ids, Map.of(5L, "Céramique")).get(1L);

        assertThat(answers)
                .containsEntry(fieldId("description"), "Tesson")
                .containsEntry(fieldId("taq"), 1200)
                .containsEntry(fieldId("category"), new ResourceRef("5", "concepts", "Céramique"))
                .containsEntry(fieldId("recordingUnit"), new ResourceRef("42", "recording-units", "INST-P-UE42"));
        List<?> authors = (List<?>) answers.get(fieldId("authors"));
        assertThat(authors).hasSize(1);
        ResourceRef authorRef = (ResourceRef) authors.get(0);
        assertThat(authorRef.resourceId()).isEqualTo("3");
        assertThat(authorRef.resourceType()).isEqualTo("persons");
    }

    @Test
    void project_multiValuedConceptsAndContainers_becomeRefLists() {
        SpecimenDTO dto = find(2L);
        dto.setMaterial(Set.of(concept(7L)));
        ContainerDTO box = new ContainerDTO();
        box.setId(9L);
        box.setIdentifier("CAISSE-9");
        dto.setContainers(Set.of(box));

        Map<String, Object> answers = projector.project(List.of(dto),
                new LinkedHashSet<>(List.of(fieldId("material"), fieldId("containers"))), Map.of(7L, "Os")).get(2L);

        assertThat(answers.get(fieldId("material"))).isEqualTo(List.of(new ResourceRef("7", "concepts", "Os")));
        assertThat(answers.get(fieldId("containers"))).isEqualTo(List.of(new ResourceRef("9", "containers", "CAISSE-9")));
    }

    @Test
    void collectConcepts_includesConceptsInsideCollections() {
        SpecimenDTO dto = find(3L);
        dto.setCategory(concept(1L));
        dto.setMaterial(Set.of(concept(2L)));

        List<ConceptDTO> concepts = projector.collectConcepts(List.of(dto),
                new LinkedHashSet<>(List.of(fieldId("category"), fieldId("material"))));

        assertThat(concepts).extracting(ConceptDTO::getId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void project_nullValuesStayNull() {
        Map<String, Object> answers = projector.project(List.of(find(4L)), Set.of(fieldId("description")), Map.of()).get(4L);
        assertThat(answers).containsEntry(fieldId("description"), null);
    }
}
