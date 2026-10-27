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
import static org.mockito.ArgumentMatchers.*;
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
        stub(RelationField.RECORDING_UNIT_ANTERIOR, List.of(strati("4", "anterior"), strati("7", "anterior")), 2);
        stub(RelationField.RECORDING_UNIT_SYNCHRONOUS, List.of(strati("5", "synchronous")), 1);
        stub(RelationField.RECORDING_UNIT_POSTERIOR, List.of(strati("6", "posterior")), 1);

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
        // Each stratigraphic zone is a field of its own, the one that gives the vocabulary of its types.
        assertThat(graph.fields().anterior()).isEqualTo("-328");
        assertThat(graph.fields().posterior()).isEqualTo("-329");
        assertThat(graph.fields().synchronous()).isEqualTo("-330");
    }

    @Test
    void listsTheRelationsOfTheKindsAskedFor_inTheOrderOfTheKinds() {
        RelationFieldService.KindedPage page = new RelationFieldService.KindedPage(List.of(), 0);
        when(relations.pageAcross(any(), eq(42L), eq(10), eq(20), eq("us"), eq(true), eq(false), eq("fr"))).thenReturn(page);

        RelationFieldService.KindedPage result = service.relationsOf(unit(),
                List.of(RecordingUnitRelationKind.SYNCHRONOUS, RecordingUnitRelationKind.PARENTS), "us", true, false, 10, 20, "fr");

        assertThat(result).isSameAs(page);
        org.mockito.ArgumentCaptor<java.util.Map<String, RelationField>> kinds = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(relations).pageAcross(kinds.capture(), eq(42L), eq(10), eq(20), eq("us"), eq(true), eq(false), eq("fr"));
        // The enum's own order, whatever order they were asked in.
        assertThat(kinds.getValue()).containsOnlyKeys("parents", "synchronous")
                .containsEntry("parents", RelationField.RECORDING_UNIT_PARENTS)
                .containsEntry("synchronous", RelationField.RECORDING_UNIT_SYNCHRONOUS);
        assertThat(kinds.getValue().keySet()).containsExactly("parents", "synchronous");
    }

    @Test
    void listsEveryKind_whenNoneIsAskedFor() {
        when(relations.pageAcross(any(), anyLong(), anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(new RelationFieldService.KindedPage(List.of(), 0));

        service.relationsOf(unit(), List.of(), null, false, true, 0, 50, "fr");

        org.mockito.ArgumentCaptor<java.util.Map<String, RelationField>> kinds = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        org.mockito.Mockito.verify(relations).pageAcross(kinds.capture(), anyLong(), anyInt(), anyInt(), any(), anyBoolean(), anyBoolean(), any());
        assertThat(kinds.getValue().keySet()).containsExactly("parents", "children", "anterior", "posterior", "synchronous");
    }

    @Test
    void cutsAZoneAndKeepsItsTotal() {
        List<ResourceRef> many = new ArrayList<>(IntStream.range(0, 40).mapToObj(i -> strati("a" + i, "anterior")).toList());
        stub(RelationField.RECORDING_UNIT_PARENTS, List.of(), 0);
        stub(RelationField.RECORDING_UNIT_CHILDREN, List.of(), 0);
        stub(RelationField.RECORDING_UNIT_ANTERIOR, many, 40);
        stub(RelationField.RECORDING_UNIT_SYNCHRONOUS, List.of(), 0);
        stub(RelationField.RECORDING_UNIT_POSTERIOR, List.of(), 0);

        RecordingUnitRelationGraphResource graph = service.graphOf(unit(), "fr");

        assertThat(graph.anterior().values()).hasSize(RecordingUnitRelationGraphService.ZONE_LIMIT);
        assertThat(graph.anterior().total()).isEqualTo(40);
        assertThat(graph.anterior().complete()).isFalse();
        assertThat(graph.posterior().total()).isZero();
    }
}
