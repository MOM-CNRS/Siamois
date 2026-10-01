package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.resource.form.MeasurementRef;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.form.SelectManyFieldAnswer;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiValueAnswersTest {

    private final RelationFieldService relations = mock(RelationFieldService.class);
    private final MultiValueAnswers answers = new MultiValueAnswers(relations);

    private static final String PARENTS = id("parents");
    private static final String STRATIGRAPHY = id("stratigraphicRelationships");

    @Test
    void cutsEveryListOfReferences_andLeavesOtherValuesAlone() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("-324", List.of(ref("1"), ref("2"), ref("3")));
        row.put("-302", "texte");
        row.put("-322", new MeasurementRef(1.0, "m", 1.0, null));
        row.put("-303", null);

        Map<String, Object> shaped = answers.shape(RecordingUnit.class, Map.of(42L, row), null, 1, "fr").get(42L);

        assertThat(shaped).containsEntry("-324", new MultiValue(List.of(ref("1")), 3, false,
                new MultiValue.Links("/api/v1/recording-units/42/fields/-324/values")))
                .containsEntry("-302", "texte");
        assertThat(shaped.get("-322")).isInstanceOf(MeasurementRef.class);
        assertThat(shaped).containsEntry("-303", null);
        verify(relations, never()).previews(any(), any(), anyInt(), any(), any());
    }

    @Test
    void cutsAnEnvelopeInPlace() {
        SelectManyFieldAnswer envelope = new SelectManyFieldAnswer("SELECT_MULTIPLE_PHASE", null, List.of(ref("1"), ref("2")));

        Object shaped = answers.shapeOne(Phase.class, 7L, Map.of("99", envelope), null, 1, "fr").get("99");

        assertThat(shaped).isInstanceOfSatisfying(SelectManyFieldAnswer.class, cut -> {
            assertThat(cut.values()).hasSize(1);
            assertThat(cut.total()).isEqualTo(2);
            assertThat(cut.complete()).isFalse();
            assertThat(cut.links().values()).isEqualTo("/api/v1/phases/7/fields/99/values");
        });
    }

    @Test
    void readsTheRequestedRelationFields_forTheWholePageAtOnce() {
        MultiValue preview = new MultiValue(List.of(ref("9")), 12, false, new MultiValue.Links("x"));
        when(relations.previews(eq(RelationField.RECORDING_UNIT_PARENTS), eq(Set.of(1L, 2L)), eq(1), eq("fr"), any()))
                .thenReturn(Map.of(1L, preview, 2L, MultiValue.complete(List.of())));
        Map<Long, Map<String, Object>> page = new LinkedHashMap<>();
        page.put(1L, new LinkedHashMap<>(Map.of(PARENTS, "ignored")));
        page.put(2L, new LinkedHashMap<>());

        Map<Long, Map<String, Object>> shaped = answers.shape(RecordingUnit.class, page, Set.of(PARENTS), 1, "fr");

        assertThat(shaped.get(1L)).containsEntry(PARENTS, preview);
        assertThat(shaped.get(2L)).containsEntry(PARENTS, MultiValue.complete(List.of()));
        // Not requested: not read.
        verify(relations, never()).previews(eq(RelationField.RECORDING_UNIT_STRATIGRAPHY), any(), anyInt(), any(), any());
    }

    @Test
    void aRelationFieldKeepsItsEnvelope_onADetail() {
        SelectManyFieldAnswer envelope = new SelectManyFieldAnswer("SELECT_MULTIPLE_STRATIGRAPHY", null, List.of());
        MultiValue preview = MultiValue.complete(List.of(ref("3")));
        when(relations.previews(eq(RelationField.RECORDING_UNIT_STRATIGRAPHY), any(), eq(50), eq("fr"), any()))
                .thenReturn(Map.of(42L, preview));

        Object shaped = answers.shapeOne(RecordingUnit.class, 42L, Map.of(STRATIGRAPHY, envelope),
                Set.of(STRATIGRAPHY), 50, "fr").get(STRATIGRAPHY);

        assertThat(shaped).isInstanceOfSatisfying(SelectManyFieldAnswer.class, answer -> {
            assertThat(answer.answerType()).isEqualTo("SELECT_MULTIPLE_STRATIGRAPHY");
            assertThat(answer.values()).containsExactly(ref("3"));
        });
    }

    @Test
    void knowsEveryRelationFieldOfTheDetailsForms() {
        assertThat(MultiValueAnswers.relationFieldOf(RecordingUnit.class, PARENTS)).contains(RelationField.RECORDING_UNIT_PARENTS);
        assertThat(MultiValueAnswers.relationFieldOf(RecordingUnit.class, id("specimenList"))).contains(RelationField.RECORDING_UNIT_FINDS);
        assertThat(MultiValueAnswers.relationFieldOf(RecordingUnit.class, STRATIGRAPHY)).contains(RelationField.RECORDING_UNIT_STRATIGRAPHY);
        assertThat(MultiValueAnswers.relationFieldOf(Phase.class, idOf(ConfigurableTable.PHASE, "recordingUnits")))
                .contains(RelationField.PHASE_RECORDING_UNITS);
        assertThat(MultiValueAnswers.relationFieldOf(fr.siamois.domain.models.container.Container.class,
                idOf(ConfigurableTable.CONTENANT, "specimens"))).contains(RelationField.CONTAINER_FINDS);
        assertThat(MultiValueAnswers.relationFieldOf(RecordingUnit.class, "-324")).isEmpty();
    }

    private static String id(String binding) {
        return idOf(ConfigurableTable.UE, binding);
    }

    private static String idOf(ConfigurableTable table, String binding) {
        return String.valueOf(SystemFieldCatalog.fieldBoundTo(table, binding).getId());
    }

    private static ResourceRef ref(String id) {
        return new ResourceRef(id, "recording-units", "US " + id);
    }
}
