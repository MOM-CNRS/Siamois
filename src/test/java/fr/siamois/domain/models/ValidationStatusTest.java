package fr.siamois.domain.models;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationStatusTest {

    @Test
    void nextInCycle_followsTheJsfCycle_andCancelledReentersAtIncomplete() {
        assertThat(ValidationStatus.INCOMPLETE.nextInCycle()).isEqualTo(ValidationStatus.COMPLETE);
        assertThat(ValidationStatus.COMPLETE.nextInCycle()).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(ValidationStatus.VALIDATED.nextInCycle()).isEqualTo(ValidationStatus.INCOMPLETE);
        assertThat(ValidationStatus.CANCELLED.nextInCycle()).isEqualTo(ValidationStatus.INCOMPLETE);
    }

    @ParameterizedTest
    @CsvSource({
            // reaching VALIDATED, or leaving it, needs the validator right
            "INCOMPLETE, VALIDATED, true",
            "COMPLETE,   VALIDATED, true",
            "CANCELLED,  VALIDATED, true",
            "VALIDATED,  INCOMPLETE, true",
            "VALIDATED,  CANCELLED, true",
            // everything else is an editor's move
            "INCOMPLETE, COMPLETE,  false",
            "COMPLETE,   CANCELLED, false",
            "CANCELLED,  INCOMPLETE, false",
            "VALIDATED,  VALIDATED, false"
    })
    void requiresValidatorTo(ValidationStatus from, ValidationStatus to, boolean expected) {
        assertThat(from.requiresValidatorTo(to)).isEqualTo(expected);
    }
}
