package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.MeasurementAnswerDTO;
import fr.siamois.dto.entity.SpatialUnitSummaryDTO;
import fr.siamois.dto.entity.UnitDefinitionDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.resource.form.MeasurementRef;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerAnswersProjectorTest {

    private final ContainerAnswersProjector projector = new ContainerAnswersProjector();

    private static String idOf(String binding) {
        return SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.CONTENANT).stream()
                .filter(f -> binding.equals(f.getValueBinding()))
                .map(f -> String.valueOf(f.getId()))
                .findFirst().orElseThrow();
    }

    private static Set<String> allIds() {
        return SystemFieldCatalog.sharedFieldsOf(ConfigurableTable.CONTENANT).stream()
                .map(f -> String.valueOf(f.getId())).collect(java.util.stream.Collectors.toSet());
    }

    private static ConceptDTO concept(long id) {
        ConceptDTO c = new ConceptDTO();
        c.setId(id);
        c.setExternalId("ext" + id);
        return c;
    }

    private static ContainerDTO container(long id) {
        ContainerDTO dto = new ContainerDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    void resolveRequestedFieldIds_handlesAbsentAllAndListedIds() {
        assertThat(projector.resolveRequestedFieldIds(null)).isNull();
        assertThat(projector.resolveRequestedFieldIds("")).isNull();
        assertThat(projector.resolveRequestedFieldIds("all")).containsExactlyInAnyOrderElementsOf(allIds());
        assertThat(projector.resolveRequestedFieldIds(idOf("identifier") + ",42424242")).containsExactly(idOf("identifier"));
        assertThat(ContainerAnswersProjector.fieldById(idOf("identifier"))).isNotNull();
    }

    @Test
    void project_readsTextConceptSpatialUnitAndMeasurement() {
        ContainerDTO dto = container(1L);
        dto.setIdentifier("C-1");
        dto.setType(concept(5));
        SpatialUnitSummaryDTO su = new SpatialUnitSummaryDTO();
        su.setId(9L);
        su.setName("Lieu");
        dto.setSpatialUnit(su);
        MeasurementAnswerDTO length = new MeasurementAnswerDTO();
        length.setNumericValue(2.5);
        length.setNormalizedValue(0.025);
        length.setComment("ok");
        length.setUnit(UnitDefinitionDTO.builder().symbol("cm").build());
        dto.setLength(length);
        MeasurementAnswerDTO noUnit = new MeasurementAnswerDTO();
        noUnit.setNumericValue(1.0);
        dto.setWidth(noUnit);

        Map<String, Object> answers = projector.project(List.of(dto),
                Set.of(idOf("identifier"), idOf("type"), idOf("spatialUnit"), idOf("length"), idOf("width")),
                Map.of(5L, "Boîte")).get(1L);

        assertThat(answers.get(idOf("identifier"))).isEqualTo("C-1");
        assertThat(answers.get(idOf("type"))).isEqualTo(new ResourceRef("5", "concepts", "Boîte"));
        assertThat(answers.get(idOf("spatialUnit"))).isEqualTo(new ResourceRef("9", "spatial-units", "Lieu"));
        assertThat(answers.get(idOf("length"))).isEqualTo(new MeasurementRef(2.5, "cm", 0.025, "ok"));
        assertThat(((MeasurementRef) answers.get(idOf("width"))).symbol()).isNull();
    }

    @Test
    void project_nullBindingsStayNull_andEmptyInputsGiveNothing() {
        Map<String, Object> answers = projector.projectOne(container(2L), Map.of());
        assertThat(answers).isNotEmpty();
        assertThat(answers.values()).containsOnlyNulls();

        assertThat(projector.project(null, Set.of(idOf("identifier")), Map.of())).isEmpty();
        assertThat(projector.project(List.of(container(1L)), Set.of(), Map.of())).isEmpty();
        assertThat(projector.project(Arrays.asList(null, new ContainerDTO()), Set.of(idOf("identifier")), null)).isEmpty();
        assertThat(projector.projectOne(null, Map.of())).isEmpty();
        assertThat(projector.projectOne(new ContainerDTO(), Map.of())).isEmpty();
    }

    @Test
    void collectConcepts_gathersTheConceptBindings() {
        ContainerDTO dto = container(3L);
        dto.setType(concept(7));

        assertThat(projector.collectConcepts(Arrays.asList(dto, null), Set.of(idOf("type"), idOf("identifier"))))
                .extracting(ConceptDTO::getId).containsExactly(7L);
        assertThat(projector.collectConcepts(null, Set.of(idOf("type")))).isEmpty();
        assertThat(projector.collectConcepts(List.of(dto), null)).isEmpty();
    }
}
