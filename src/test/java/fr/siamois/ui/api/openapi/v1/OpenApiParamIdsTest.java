package fr.siamois.ui.api.openapi.v1;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiParamIdsTest {

    @Test
    void requireNonBlank_trimsAndReturnsValue() {
        assertThat(OpenApiParamIds.requireNonBlank("  abc  ", "param")).isEqualTo("abc");
    }

    @Test
    void requireNonBlank_nullOrBlank_throws400() {
        assertThatThrownBy(() -> OpenApiParamIds.requireNonBlank(null, "x"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> OpenApiParamIds.requireNonBlank("  ", "x"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("x est obligatoire");
    }

    @Test
    void parseRequiredConceptId_parsesNumericString() {
        assertThat(OpenApiParamIds.parseRequiredConceptId("42", "conceptId")).isEqualTo(42L);
    }

    @Test
    void parseRequiredConceptId_invalidFormat_throws400() {
        assertThatThrownBy(() -> OpenApiParamIds.parseRequiredConceptId("abc", "specimenTypeConceptId"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .asString()
                .contains("specimenTypeConceptId invalide");
    }
}
