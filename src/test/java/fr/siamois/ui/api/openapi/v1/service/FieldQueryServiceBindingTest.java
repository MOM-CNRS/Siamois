package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A form binds the DTO's property name; a list sorts and filters on the JPA entity's own attribute.
 */
class FieldQueryServiceBindingTest {

    @Test
    void aRecordingUnitsChronologicalPhaseIsTheEntitysChronologicalAttribution() {
        assertThat(FieldQueryService.entityAttribute(RecordingUnit.class, "chronologicalPhase"))
                .isEqualTo("chronologicalAttribution");
    }

    @Test
    void aBindingWithoutAnAliasIsTheAttributeItself() {
        assertThat(FieldQueryService.entityAttribute(RecordingUnit.class, "fullIdentifier")).isEqualTo("fullIdentifier");
        assertThat(FieldQueryService.entityAttribute(RecordingUnit.class, "zInf")).isEqualTo("zInf");
    }

    @Test
    void anAliasOnlyAppliesToItsOwnEntity() {
        assertThat(FieldQueryService.entityAttribute(Specimen.class, "chronologicalPhase")).isEqualTo("chronologicalPhase");
        assertThat(FieldQueryService.entityAttribute(Container.class, "chronologicalPhase")).isEqualTo("chronologicalPhase");
    }
}
