package fr.siamois.ui.api.openapi.v1.request.list;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValuesLimitTest {

    @Test
    void parse_fallsBackWhenAbsent_andAcceptsZeroToMax() {
        assertThat(ValuesLimit.parse(null, 1)).isEqualTo(1);
        assertThat(ValuesLimit.parse(" ", 50)).isEqualTo(50);
        assertThat(ValuesLimit.parse("0", 1)).isZero();
        assertThat(ValuesLimit.parse("200", 1)).isEqualTo(200);
    }

    @Test
    void parse_rejectsOutOfRangeOrNonNumeric() {
        assertThatThrownBy(() -> ValuesLimit.parse("201", 1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ValuesLimit.parse("-1", 1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> ValuesLimit.parse("tout", 1)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void outsideARequest_theDefaultsApply() {
        assertThat(ValuesLimit.forList()).isEqualTo(ValuesLimit.LIST_DEFAULT);
        assertThat(ValuesLimit.forDetail()).isEqualTo(ValuesLimit.DETAIL_DEFAULT);
    }
}
