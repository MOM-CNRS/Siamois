package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.infrastructure.database.repositories.relation.DefinitionRelation;
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
    void aConfigurableRelationsValueCarriesItsTypeAndCertainty_butNoPosition() {
        DefinitionRelation relation = DefinitionRelation.parse(fr.siamois.domain.models.relation.RelationEntityKind.RECORDING_UNIT,
                "relation:SIAMOIS.RELATION.ORDER:SOURCE:RECORDING_UNIT").orElseThrow();
        when(repository.page(relation, 1L, 0, 50, null, true)).thenReturn(List.of(
                new RelationFieldRepository.Row(1L, 2L, "US 2", "source", 77L, "ext", null, null, true)));
        when(repository.count(relation, 1L, null)).thenReturn(1L);
        when(labels.resolveLabels(any(), eq("fr"))).thenReturn(Map.of(77L, "recoupe"));

        ResourceRef value = service.page(relation, 1L, 0, 50, null, true, "fr").values().get(0);

        assertThat(value.qualifier().concept().label()).isEqualTo("recoupe");
        assertThat(value.qualifier().uncertain()).isTrue();
        assertThat(value.qualifier().role()).isEqualTo("source");
        // the field is named by the side it lists: nothing to sort the values into
        assertThat(value.qualifier().position()).isNull();
    }

    private static Row row(long owner, long target, String label, String role, Boolean asynchronous, Long conceptId) {
        return new Row(owner, target, label, role, conceptId, conceptId == null ? null : "ext" + conceptId,
                asynchronous, null, false);
    }
}
