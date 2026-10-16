package fr.siamois.domain.models.relation;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.models.vocabulary.Concept;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RelationLinkTest {

    private static RecordingUnit unit(long id) {
        RecordingUnit unit = new RecordingUnit();
        unit.setId(id);
        return unit;
    }

    @Test
    void attach_setsTheEndOfTheEntitysKindAndClearsTheOthers() {
        RelationLink link = new RelationLink();

        assertThat(link.attachSource(unit(7))).isTrue();
        assertThat(link.sourceKind()).contains(RelationEntityKind.RECORDING_UNIT);
        assertThat(link.sourceId()).contains(7L);

        Specimen specimen = new Specimen();
        specimen.setId(9L);
        assertThat(link.attachSource(specimen)).isTrue();
        assertThat(link.sourceKind()).contains(RelationEntityKind.SPECIMEN);
        assertThat(link.getSourceRecordingUnit()).isNull();
    }

    @Test
    void attach_refusesAnEntityOfAnUnsupportedKind() {
        RelationLink link = new RelationLink();
        link.attachSource(unit(1));

        assertThat(link.attachSource("not an entity")).isFalse();
        // the previous end is cleared either way: a link never keeps a stale end
        assertThat(link.sourceKind()).isEmpty();
    }

    @Test
    void aPersonIsOnlyEverATarget() {
        RelationLink link = new RelationLink();

        assertThat(link.attachSource(new Person())).isFalse();
        assertThat(link.attachTarget(new Person())).isTrue();
        assertThat(link.targetKind()).contains(RelationEntityKind.PERSON);
        assertThat(RelationEntityKind.PERSON.canBeSource()).isFalse();
        assertThat(RelationEntityKind.RECORDING_UNIT.canBeSource()).isTrue();
    }

    @Test
    void anUnsetEndHasNoKindNorId() {
        RelationLink link = new RelationLink();

        assertThat(link.targetKind()).isEmpty();
        assertThat(link.targetId()).isEmpty();
    }

    @Test
    void kindsOf_returnsTheRowsOfOneSide_severalTypesOfTheSameKind() {
        RelationDefinition definition = new RelationDefinition();
        Concept fact = new Concept();
        Concept sounding = new Concept();
        definition.addKind(RelationSide.SOURCE, RelationEntityKind.RECORDING_UNIT, null);
        definition.addKind(RelationSide.TARGET, RelationEntityKind.RECORDING_UNIT, fact);
        definition.addKind(RelationSide.TARGET, RelationEntityKind.RECORDING_UNIT, sounding);

        assertThat(definition.kindsOf(RelationSide.SOURCE)).hasSize(1);
        assertThat(definition.kindsOf(RelationSide.TARGET)).extracting(RelationDefinitionKind::getTypeConcept)
                .containsExactly(fact, sounding);
        assertThat(definition.kindsOf(RelationSide.TARGET)).allMatch(k -> k.getDefinition() == definition);
    }
}
