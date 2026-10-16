package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitRelationGraphResource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecordingUnitRelationGraphServiceTest {

    private final RelationFieldService relations = mock(RelationFieldService.class);
    private final RecordingUnitRelationGraphService service = new RecordingUnitRelationGraphService(relations);

    private static ResourceRef plain(String id) {
        return new ResourceRef(id, "recording-units", "US " + id);
    }

    private static ResourceRef strati(String id, String position) {
        return new ResourceRef(id, "recording-units", "US " + id, new ResourceRef.StratigraphicQualifier(
                null, "unit1", position, null, !"synchronous".equals(position), false));
    }

    private RecordingUnitDTO unit() {
        RecordingUnitDTO unit = new RecordingUnitDTO();
        unit.setId(42L);
        unit.setFullIdentifier("US 42");
        return unit;
    }

    private void stub(RelationField relation, List<ResourceRef> values, long total) {
        when(relations.page(eq(relation), eq(42L), eq(0), anyInt(), any(), anyBoolean(), any()))
                .thenReturn(new RelationFieldService.ValuesPage(values, total));
    }

    @Test
    void groupsTheRelationsByZone() {
        stub(RelationField.RECORDING_UNIT_PARENTS, List.of(plain("1")), 1);
        stub(RelationField.RECORDING_UNIT_CHILDREN, List.of(plain("2"), plain("3")), 2);
        stub(RelationField.RECORDING_UNIT_STRATIGRAPHY,
                List.of(strati("4", "anterior"), strati("5", "synchronous"), strati("6", "posterior"), strati("7", "anterior")), 4);

        RecordingUnitRelationGraphResource graph = service.graphOf(unit(), "fr");

        assertThat(graph.center().resourceId()).isEqualTo("42");
        assertThat(graph.center().label()).isEqualTo("US 42");
        assertThat(graph.parents().values()).extracting(ResourceRef::resourceId).containsExactly("1");
        assertThat(graph.children().values()).extracting(ResourceRef::resourceId).containsExactly("2", "3");
        assertThat(graph.anterior().values()).extracting(ResourceRef::resourceId).containsExactly("4", "7");
        assertThat(graph.synchronous().values()).extracting(ResourceRef::resourceId).containsExactly("5");
        assertThat(graph.posterior().values()).extracting(ResourceRef::resourceId).containsExactly("6");
        assertThat(graph.others().values()).isEmpty();
        assertThat(graph.others().complete()).isTrue();
    }

    @Test
    void cutsAZoneAndKeepsItsTotal() {
        List<ResourceRef> many = new ArrayList<>(IntStream.range(0, 40).mapToObj(i -> strati("a" + i, "anterior")).toList());
        stub(RelationField.RECORDING_UNIT_PARENTS, List.of(), 0);
        stub(RelationField.RECORDING_UNIT_CHILDREN, List.of(), 0);
        stub(RelationField.RECORDING_UNIT_STRATIGRAPHY, many, 40);

        RecordingUnitRelationGraphResource graph = service.graphOf(unit(), "fr");

        assertThat(graph.anterior().values()).hasSize(RecordingUnitRelationGraphService.ZONE_LIMIT);
        assertThat(graph.anterior().total()).isEqualTo(40);
        assertThat(graph.anterior().complete()).isFalse();
        assertThat(graph.posterior().total()).isZero();
    }
}
