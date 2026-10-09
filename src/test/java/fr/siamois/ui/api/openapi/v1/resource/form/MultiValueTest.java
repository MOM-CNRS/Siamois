package fr.siamois.ui.api.openapi.v1.resource.form;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiValueTest {

    private static final String HREF = "/api/v1/recording-units/42/fields/-319/values";

    @Test
    void of_cutsToTheLimit_andSaysWhereTheRestIs() {
        MultiValue value = MultiValue.of(List.of(ref("1"), ref("2"), ref("3")), 37, 1, HREF);

        assertThat(value.values()).extracting(ResourceRef::resourceId).containsExactly("1");
        assertThat(value.total()).isEqualTo(37);
        assertThat(value.complete()).isFalse();
        assertThat(value.links()).isEqualTo(new MultiValue.Links(HREF));
    }

    @Test
    void of_isCompleteWithNoLink_whenNothingWasCut() {
        MultiValue value = MultiValue.of(List.of(ref("1"), ref("2")), 2, 50, HREF);

        assertThat(value.complete()).isTrue();
        assertThat(value.links()).isNull();
    }

    @Test
    void of_limitZero_keepsOnlyTheTotal() {
        MultiValue value = MultiValue.of(List.of(ref("1")), 1, 0, HREF);

        assertThat(value.values()).isEmpty();
        assertThat(value.total()).isEqualTo(1);
        assertThat(value.complete()).isFalse();
    }

    @Test
    void selectManyAnswer_carriesTheSameShape() {
        SelectManyFieldAnswer answer = new SelectManyFieldAnswer("SELECT_MULTIPLE_RECORDING_UNIT", null, List.of(ref("1")));
        assertThat(answer.total()).isEqualTo(1);
        assertThat(answer.complete()).isTrue();

        SelectManyFieldAnswer cut = answer.with(MultiValue.of(List.of(ref("1")), 5, 1, HREF));
        assertThat(cut.answerType()).isEqualTo("SELECT_MULTIPLE_RECORDING_UNIT");
        assertThat(cut.total()).isEqualTo(5);
        assertThat(cut.links()).isEqualTo(new MultiValue.Links(HREF));
    }

    @Test
    void resourceRef_hrefPointsAtTheResourceDetail_whenItHasOne() {
        assertThat(ref("12").href()).isEqualTo("/api/v1/recording-units/12");
        assertThat(new ResourceRef("5", "projects", "P").href()).isEqualTo("/api/v1/projects/5");
        assertThat(new ResourceRef("5", "persons", "Jean").href()).isNull();
    }

    private static ResourceRef ref(String id) {
        return new ResourceRef(id, "recording-units", "US " + id);
    }
}
