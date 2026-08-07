package fr.siamois.domain.services.identifier;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierResolverTest {

    private final IdentifierResolver<IdentifierRenderContext> number =
            new NumericalIdentifierResolver<>("NUM_TEST", "title", "description", null);
    private final IdentifierResolver<IdentifierRenderContext> text =
            new TextIdentifierResolver<>("ID_TEST", "title", "description", null);

    @Test
    void numericalResolver_shouldRenderRawAndPaddedValues() {
        IdentifierRenderContext context = new MapIdentifierRenderContext(Map.of("NUM_TEST", 42));

        assertThat(number.render("A-{NUM_TEST}-B", context)).isEqualTo("A-42-B");
        assertThat(number.render("A-{NUM_TEST:0000}-B", context)).isEqualTo("A-0042-B");
    }

    @Test
    void numericalResolver_shouldRenderMissingValueWithConfiguredNumberOfZeroes() {
        Map<String, Object> values = new HashMap<>();
        values.put("NUM_TEST", null);
        IdentifierRenderContext context = new MapIdentifierRenderContext(values);

        assertThat(number.render("{NUM_TEST}", context)).isEqualTo("0");
        assertThat(number.render("{NUM_TEST:00}", context)).isEqualTo("00");
        assertThat(number.render("{NUM_TEST:0000}", context)).isEqualTo("0000");
    }

    @Test
    void textResolver_shouldRenderValueOrUniversalMissingPlaceholder() {
        assertThat(text.render("{ID_TEST}", new MapIdentifierRenderContext(Map.of("ID_TEST", "UE-0042"))))
                .isEqualTo("UE-0042");
        assertThat(text.render("{ID_TEST}", new MapIdentifierRenderContext(Map.of())))
                .isEqualTo("XXX");
    }

    @Test
    void numericalResolver_shouldRejectNonNumericalValue() {
        IdentifierRenderContext context = new MapIdentifierRenderContext(Map.of("NUM_TEST", "42"));

        assertThatThrownBy(() -> number.render("{NUM_TEST}", context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a Number");
    }
}
