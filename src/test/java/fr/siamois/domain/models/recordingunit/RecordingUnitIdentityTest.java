package fr.siamois.domain.models.recordingunit;

import fr.siamois.domain.models.actionunit.ActionUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingUnitIdentityTest {

    @Test
    @DisplayName("Two loads of the same unit are equal on the business key")
    void sameFullIdentifierInSameActionUnitIsTheSameUnit() {
        RecordingUnit loadedForTheTable = unit(1L, "US 1", 10L);
        RecordingUnit loadedForTheForm = unit(2L, "US 1", 10L);

        assertThat(loadedForTheTable)
                .isEqualTo(loadedForTheForm)
                .hasSameHashCodeAs(loadedForTheForm);
        assertThat(Set.of(loadedForTheForm)).contains(loadedForTheTable);
    }

    /** The whole point of the composite key: an identifier is only unique inside its action unit. */
    @Test
    @DisplayName("The same identifier in two action units are two different units")
    void sameFullIdentifierInAnotherActionUnitIsAnotherUnit() {
        RecordingUnit inFirstAction = unit(1L, "US 1", 10L);
        RecordingUnit inSecondAction = unit(2L, "US 1", 20L);

        assertThat(inFirstAction).isNotEqualTo(inSecondAction);
        assertThat(Set.of(inSecondAction)).doesNotContain(inFirstAction);
    }

    @Test
    @DisplayName("Different identifiers in one action unit stay distinct")
    void differentFullIdentifiersStayDistinct() {
        RecordingUnit one = unit(1L, "US 1", 10L);
        RecordingUnit two = unit(2L, "US 2", 10L);

        assertThat(one).isNotEqualTo(two);
        assertThat(new HashSet<>(Set.of(one, two))).hasSize(2);
    }

    /**
     * The duplication bug: a unit is saved with a placeholder identifier, added to its parent's
     * children, and only then given its real one. It has to stay findable across that mutation.
     */
    @Test
    @DisplayName("A unit stays findable in a set after its full identifier is generated")
    void unitStaysFindableAfterItsIdentifierChanges() {
        RecordingUnit newlyCreated = unit(1L, "TEMP-PLACEHOLDER", 10L);
        Set<RecordingUnit> children = new HashSet<>();
        children.add(newlyCreated);

        newlyCreated.setFullIdentifier("US 42");

        assertThat(children).contains(newlyCreated);
        assertThat(children.iterator().next().getFullIdentifier()).isEqualTo("US 42");
    }

    @Test
    @DisplayName("Units without a business key yet fall back to their id")
    void unsavedUnitsFallBackToTheirId() {
        RecordingUnit one = new RecordingUnit();
        one.setId(7L);
        RecordingUnit sameRow = new RecordingUnit();
        sameRow.setId(7L);
        RecordingUnit otherRow = new RecordingUnit();
        otherRow.setId(8L);

        assertThat(one).isEqualTo(sameRow).isNotEqualTo(otherRow);
    }

    @Test
    @DisplayName("A transient unit with neither key is equal only to itself")
    void transientUnitsAreEqualOnlyToThemselves() {
        RecordingUnit one = new RecordingUnit();
        RecordingUnit another = new RecordingUnit();

        assertThat(one).isEqualTo(one).isNotEqualTo(another);
        assertThat(new HashSet<>(Set.of(one, another))).hasSize(2);
    }

    private RecordingUnit unit(Long id, String fullIdentifier, Long actionUnitId) {
        ActionUnit actionUnit = new ActionUnit();
        actionUnit.setId(actionUnitId);

        RecordingUnit unit = new RecordingUnit();
        unit.setId(id);
        unit.setFullIdentifier(fullIdentifier);
        unit.setActionUnit(actionUnit);
        return unit;
    }
}
