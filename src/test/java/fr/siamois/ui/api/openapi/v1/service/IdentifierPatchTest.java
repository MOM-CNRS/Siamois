package fr.siamois.ui.api.openapi.v1.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

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

    @Test
    void aChosenIdentifierIsSetTrimmedOnceFree() {
        List<String> set = new ArrayList<>();

        assertThat(IdentifierPatch.applyChosen("  UE-12 ", set::add, () -> false)).isTrue();

        assertThat(set).containsExactly("UE-12");
    }

    @Test
    void anAbsentOrBlankChosenIdentifierLeavesGenerationToTheServer() {
        List<String> set = new ArrayList<>();
        BooleanSupplier neverAsked = () -> {
            throw new AssertionError("no uniqueness check without a chosen identifier");
        };

        assertThat(IdentifierPatch.applyChosen(null, set::add, neverAsked)).isFalse();
        assertThat(IdentifierPatch.applyChosen("   ", set::add, neverAsked)).isFalse();

        assertThat(set).isEmpty();
    }

    @Test
    void aChosenIdentifierAlreadyUsedIsAConflict() {
        assertThatThrownBy(() -> IdentifierPatch.applyChosen("UE-12", value -> { }, () -> true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
