package fr.siamois.ui.api.openapi.v1.request.recordingunit;

import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldAnswerMapsTest {

    @Test
    void delta_isNull_forAnAnswerThatSetsItsValue() {
        assertThat(FieldAnswerMaps.delta(new AnswerInput(null, List.of(1)))).isNull();
        assertThat(FieldAnswerMaps.delta(Map.of("values", List.of(1)))).isNull();
        assertThat(FieldAnswerMaps.delta("x")).isNull();
    }

    @Test
    void delta_readsAnAnswerInputOrARawMap() {
        assertThat(FieldAnswerMaps.delta(new AnswerInput(null, null, List.of(1), null)))
                .isEqualTo(new FieldAnswerMaps.Delta(List.of(1), List.of()));
        assertThat(FieldAnswerMaps.delta(Map.of("remove", List.of("2"))))
                .isEqualTo(new FieldAnswerMaps.Delta(List.of(), List.of("2")));
    }

    @Test
    void delta_rejectsValuesTogetherWithAddRemove_andANonListAdd() {
        var arg1 = new AnswerInput(null, List.of(1), List.of(2), null);
        assertThatThrownBy(() -> FieldAnswerMaps.delta(arg1))
                .isInstanceOf(ResponseStatusException.class);
        var notAList = Map.of("add", "3");
        assertThatThrownBy(() -> FieldAnswerMaps.delta(notAList))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void applyTo_removesThenAppends_withoutDuplicates_keepingTheCurrentOrder() {
        FieldAnswerMaps.Delta delta = new FieldAnswerMaps.Delta(List.of(), List.of("2", 9));
        List<Long> result = delta.applyTo(List.of(1L, 2L, 3L), List.of(3L, 4L),
                item -> Long.valueOf(String.valueOf(item)), new ArrayList<>());

        assertThat(result).containsExactly(1L, 3L, 4L);
    }

    @Test
    void merge_keepsADeltaAsItIs_andUnwrapsTheRest() {
        AnswerInput delta = new AnswerInput(null, null, List.of(1), null);
        Map<String, Object> merged = FieldAnswerMaps.merge(
                Map.of("1", delta, "2", new AnswerInput("titre", null)), null);

        assertThat(merged.get("1")).isSameAs(delta);
        assertThat(merged).containsEntry("2", "titre");
    }
}
