package fr.siamois.ui.api.openapi.v1.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierPatchTest {

    @Test
    void anAbsentIdentifierIsLeftAlone() {
        assertThat(IdentifierPatch.requested(null)).isNull();
    }

    @Test
    void theRequestedIdentifierIsTrimmed() {
        assertThat(IdentifierPatch.requested("  UE-12 ")).isEqualTo("UE-12");
    }

    @Test
    void aBlankIdentifierIsRejected() {
        assertThatThrownBy(() -> IdentifierPatch.requested("  ")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void onlyADifferentIdentifierIsAChange() {
        assertThat(IdentifierPatch.changes(null, "A")).isFalse();
        assertThat(IdentifierPatch.changes("A", "A")).isFalse();
        assertThat(IdentifierPatch.changes("B", "A")).isTrue();
    }

    @Test
    void aTakenIdentifierIsRejected() {
        IdentifierPatch.requireFree(false);
        assertThatThrownBy(() -> IdentifierPatch.requireFree(true)).isInstanceOf(ResponseStatusException.class);
    }
}
