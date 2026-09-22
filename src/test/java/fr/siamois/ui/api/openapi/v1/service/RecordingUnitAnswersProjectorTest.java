package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.MeasurementRef;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.table.definitions.RecordingUnitTableColumnDefaults;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingUnitAnswersProjectorTest {

    private final RecordingUnitAnswersProjector projector = new RecordingUnitAnswersProjector();

    /**
     * {@code RecordingUnitForm}'s own field constants are {@code protected} (unlike
     * {@code ActionUnitForm}'s public ones), so tests resolve fields the same way
     * {@code TableDefinitions.systemField} does in production code — by value binding, off the
     * shared catalog.
     */
    private static CustomField fieldFor(String valueBinding) {
        return SystemFieldCatalog.fieldsOf(ConfigurableTable.UE).stream()
                .filter(f -> valueBinding.equals(f.getValueBinding()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No UE field bound to '" + valueBinding + "'"));
    }

    private static String fieldId(String valueBinding) {
        return String.valueOf(fieldFor(valueBinding).getId());
    }

    private static ConceptDTO concept(long id, String externalId) {
        ConceptDTO c = new ConceptDTO();
        c.setId(id);
        c.setExternalId(externalId);
        return c;
    }

    private static RecordingUnitDTO recordingUnit(long id) {
        RecordingUnitDTO dto = new RecordingUnitDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    void resolveRequestedFieldIds_absentParam_meansNoProjection() {
        assertThat(projector.resolveRequestedFieldIds(null)).isNull();
        assertThat(projector.resolveRequestedFieldIds("  ")).isNull();
    }

    @Test
    void resolveRequestedFieldIds_defaultMatchesTheTableColumnDefaults() {
        assertThat(projector.resolveRequestedFieldIds("default"))
                .isEqualTo(RecordingUnitTableColumnDefaults.defaultVisibleFieldIds())
                .contains(fieldId("type"), fieldId("author"))
                .doesNotContain(fieldId("matrixColor"));
    }

    @Test
    void resolveRequestedFieldIds_allCoversEveryDetailsFormField() {
        Set<String> all = projector.resolveRequestedFieldIds("all");
        assertThat(all).contains(fieldId("type"), fieldId("author"), fieldId("tpq"));
    }

    @Test
    void resolveRequestedFieldIds_ignoresUnknownIds() {
        Set<String> ids = projector.resolveRequestedFieldIds(fieldId("author") + ",999999, ");
        assertThat(ids).containsExactly(fieldId("author"));
    }

    @Test
    void project_readsScalarBindings() {
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setDescription("Une couche de remblai");
        dto.setTpq(-500);

        Map<String, Object> answers = projector.project(
                List.of(dto), Set.of(fieldId("description"), fieldId("tpq")), Map.of()).get(1L);

        assertThat(answers).containsEntry(fieldId("description"), "Une couche de remblai");
        assertThat(answers).containsEntry(fieldId("tpq"), -500);
    }

    @Test
    void project_emitsResourceRefForSelectOneConcept() {
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setType(concept(7L, "1234"));

        Map<String, Object> answers = projector.project(
                List.of(dto), Set.of(fieldId("type")), Map.of(7L, "Couche")).get(1L);

        assertThat(answers.get(fieldId("type"))).isEqualTo(new ResourceRef("7", "concepts", "Couche"));
    }

    @Test
    void project_emitsResourceRefForSelectOnePerson_author() {
        PersonDTO author = new PersonDTO();
        author.setId(3L);
        author.setName("Jeanne");
        author.setLastname("Dupont");
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setAuthor(author);

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(fieldId("author")), Map.of()).get(1L);

        assertThat(answers.get(fieldId("author"))).isEqualTo(new ResourceRef("3", "persons", "Jeanne Dupont"));
    }

    @Test
    void project_emitsResourceRefListForSelectMultiplePerson_contributors() {
        PersonDTO p1 = new PersonDTO();
        p1.setId(4L);
        p1.setName("A");
        p1.setLastname("B");
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setContributors(List.of(p1));

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(fieldId("contributors")), Map.of()).get(1L);

        assertThat(answers.get(fieldId("contributors"))).isEqualTo(List.of(new ResourceRef("4", "persons", "A B")));
    }

    @Test
    void project_emitsResourceRefForSelectOneActionUnit() {
        ActionUnitSummaryDTO au = new ActionUnitSummaryDTO();
        au.setId(9L);
        au.setFullIdentifier("INST-PROJ");
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setActionUnit(au);

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(fieldId("actionUnit")), Map.of()).get(1L);

        assertThat(answers.get(fieldId("actionUnit"))).isEqualTo(new ResourceRef("9", "action-units", "INST-PROJ"));
    }

    @Test
    void project_emitsResourceRefForSelectOneSpatialUnit() {
        SpatialUnitSummaryDTO su = new SpatialUnitSummaryDTO();
        su.setId(11L);
        su.setName("Zone A");
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setSpatialUnit(su);

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(fieldId("spatialUnit")), Map.of()).get(1L);

        assertThat(answers.get(fieldId("spatialUnit"))).isEqualTo(new ResourceRef("11", "spatial-units", "Zone A"));
    }

    @Test
    void project_emitsResourceRefListForSelectMultiplePhase() {
        PhaseDTO phase = new PhaseDTO();
        phase.setId(21L);
        phase.setTitle("Phase 1");
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setPhases(new LinkedHashSet<>(Set.of(phase)));

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(fieldId("phases")), Map.of()).get(1L);

        assertThat(answers.get(fieldId("phases"))).isEqualTo(List.of(new ResourceRef("21", "phases", "Phase 1")));
    }

    @Test
    void project_emitsMeasurementRefForMeasurementField() {
        UnitDefinitionDTO unit = new UnitDefinitionDTO();
        unit.setSymbol("cm");
        MeasurementAnswerDTO measurement = MeasurementAnswerDTO.builder()
                .numericValue(3.5)
                .unit(unit)
                .normalizedValue(0.035)
                .comment("mesuré au clou")
                .build();
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setZInf(measurement);

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(fieldId("zInf")), Map.of()).get(1L);

        assertThat(answers.get(fieldId("zInf"))).isEqualTo(new MeasurementRef(3.5, "cm", 0.035, "mesuré au clou"));
    }

    /**
     * parents/children are never loaded on this list ({@code includeFullRelations=false}), so their
     * catalog entries always project null — React reads the counts from {@code _counts} instead.
     */
    @Test
    void project_neverProjectsParentsOrChildren_evenIfSomehowPopulated() {
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setParents(new LinkedHashSet<>());

        Map<String, Object> answers = projector.project(List.of(dto), Set.of(fieldId("parents")), Map.of()).get(1L);

        assertThat(answers).containsKey(fieldId("parents"));
        assertThat(answers.get(fieldId("parents"))).isNull();
    }

    @Test
    void project_emitsNullForAnUnsetBindingRatherThanOmittingTheKey() {
        Map<String, Object> answers = projector.project(
                List.of(recordingUnit(1L)), Set.of(fieldId("description")), Map.of()).get(1L);

        assertThat(answers).containsKey(fieldId("description"));
        assertThat(answers.get(fieldId("description"))).isNull();
    }

    @Test
    void project_withoutRequestedFields_returnsNothing() {
        assertThat(projector.project(List.of(recordingUnit(1L)), null, Map.of())).isEmpty();
        assertThat(projector.project(List.of(recordingUnit(1L)), Set.of(), Map.of())).isEmpty();
    }

    @Test
    void collectConcepts_gathersConceptsFromSingleValuedFields() {
        RecordingUnitDTO dto = recordingUnit(1L);
        dto.setType(concept(7L, "1234"));
        dto.setGeomorphologicalCycle(concept(8L, "5678"));

        List<ConceptDTO> concepts = projector.collectConcepts(
                List.of(dto), Set.of(fieldId("type"), fieldId("geomorphologicalCycle")));

        assertThat(concepts).extracting(ConceptDTO::getId).containsExactlyInAnyOrder(7L, 8L);
    }
}
