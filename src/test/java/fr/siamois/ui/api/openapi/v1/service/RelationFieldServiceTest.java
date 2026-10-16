package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.infrastructure.database.repositories.relation.RelationFieldRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationFieldRepository.Row;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelationFieldServiceTest {

    private final RelationFieldRepository repository = mock(RelationFieldRepository.class);
    private final ConceptLabelBatchResolver labels = mock(ConceptLabelBatchResolver.class);
    private final RelationFieldService service = new RelationFieldService(repository, labels);

    @Test
    void previews_giveEveryOwnerOne_emptyWhenItHasNoValue() {
        when(repository.previews(RelationField.RECORDING_UNIT_CHILDREN, List.of(1L, 2L), 1)).thenReturn(Map.of(
                1L, new RelationFieldRepository.Preview(List.of(row(1L, 10L, "US 10", null, null, null)), 4)));

        Map<Long, MultiValue> previews = service.previews(RelationField.RECORDING_UNIT_CHILDREN, List.of(1L, 2L), 1, "fr",
                owner -> "/values/" + owner);

        assertThat(previews.get(1L).values()).containsExactly(new ResourceRef("10", "recording-units", "US 10"));
        assertThat(previews.get(1L).total()).isEqualTo(4);
        assertThat(previews.get(1L).links().values()).isEqualTo("/values/1");
        assertThat(previews).containsEntry(2L, MultiValue.complete(List.of()));
    }

    @Test
    void aStratigraphicValueIsQualifiedByItsRelationship() {
        when(repository.page(RelationField.RECORDING_UNIT_POSTERIOR, 1L, 0, 50, null, true)).thenReturn(List.of(
                row(1L, 2L, "US 2", "unit1", true, 77L),
                row(1L, 3L, "US 3", "unit2", true, null),
                row(1L, 4L, "US 4", "unit1", false, null)));
        when(repository.count(RelationField.RECORDING_UNIT_POSTERIOR, 1L, null)).thenReturn(3L);
        when(labels.resolveLabels(any(), eq("fr"))).thenReturn(Map.of(77L, "coupe"));

        RelationFieldService.ValuesPage page = service.page(RelationField.RECORDING_UNIT_POSTERIOR, 1L, 0, 50, null, true, "fr");

        assertThat(page.total()).isEqualTo(3);
        ResourceRef first = page.values().get(0);
        assertThat(first.resourceType()).isEqualTo("recording-units");
        assertThat(first.qualifier().concept().label()).isEqualTo("coupe");
        assertThat(first.qualifier().role()).isEqualTo("unit1");
        assertThat(first.qualifier().position()).isEqualTo("posterior");
        assertThat(page.values().get(1).qualifier().position()).isEqualTo("anterior");
        assertThat(page.values().get(1).qualifier().concept()).isNull();
        assertThat(page.values().get(2).qualifier().position()).isEqualTo("synchronous");
    }

    @Test
    void valuesAcrossRelations_areQualifiedOnlyWhereTheirRelationIs() {
        Map<String, RelationField> kinds = new java.util.LinkedHashMap<>();
        kinds.put("parents", RelationField.RECORDING_UNIT_PARENTS);
        kinds.put("anterior", RelationField.RECORDING_UNIT_ANTERIOR);
        when(repository.pageAcross(kinds, 1L, 0, 50, null, false, true)).thenReturn(List.of(
                new RelationFieldRepository.KindedRow("parents", row(1L, 2L, "US 2", null, null, null)),
                new RelationFieldRepository.KindedRow("anterior", row(1L, 3L, "US 3", "unit2", true, 77L))));
        when(repository.countAcross(kinds, 1L, null)).thenReturn(2L);
        when(labels.resolveLabels(any(), eq("fr"))).thenReturn(Map.of(77L, "coupe"));

        RelationFieldService.KindedPage page = service.pageAcross(kinds, 1L, 0, 50, null, false, true, "fr");

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.values()).extracting(RelationFieldService.KindedValue::kind).containsExactly("parents", "anterior");
        assertThat(page.values().get(0).value().qualifier()).isNull();
        assertThat(page.values().get(1).value().qualifier().position()).isEqualTo("anterior");
        assertThat(page.values().get(1).value().qualifier().concept().label()).isEqualTo("coupe");
    }

    private static Row row(long owner, long target, String label, String role, Boolean asynchronous, Long conceptId) {
        return new Row(owner, target, label, role, conceptId, conceptId == null ? null : "ext" + conceptId,
                asynchronous, null, false);
    }
}
