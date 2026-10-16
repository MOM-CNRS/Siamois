package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingUnitRelationKindTest {

    @Test
    void everyKindIsFoundByTheNameClientsUse() {
        for (RecordingUnitRelationKind kind : RecordingUnitRelationKind.values()) {
            assertThat(RecordingUnitRelationKind.ofWireName(kind.wireName())).contains(kind);
        }
        assertThat(RecordingUnitRelationKind.ofWireName("nope")).isEmpty();
        assertThat(RecordingUnitRelationKind.ofWireName(null)).isEmpty();
    }

    @Test
    void eachKindIsReadFromItsOwnRelationField() {
        assertThat(RecordingUnitRelationKind.PARENTS.field()).isEqualTo(RelationField.RECORDING_UNIT_PARENTS);
        assertThat(RecordingUnitRelationKind.ANTERIOR.field().stratigraphicPosition()).isEqualTo("anterior");
        assertThat(RecordingUnitRelationKind.POSTERIOR.field().stratigraphicPosition()).isEqualTo("posterior");
        assertThat(RecordingUnitRelationKind.SYNCHRONOUS.field().stratigraphicPosition()).isEqualTo("synchronous");
    }
}
