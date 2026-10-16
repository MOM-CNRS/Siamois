package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.SystemRelations;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefinitionRelationTest {

    private static final String ORDER = SystemRelations.ORDER_CODE;

    private static DefinitionRelation parse(DefinitionRelation.Reading reading) {
        String binding = DefinitionRelation.bindingOf(ORDER, reading, RelationEntityKind.RECORDING_UNIT);
        return DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT, binding).orElseThrow();
    }

    @Test
    void aBindingRoundTrips() {
        String binding = DefinitionRelation.bindingOf(ORDER, DefinitionRelation.Reading.TARGET, RelationEntityKind.RECORDING_UNIT);

        assertThat(binding).isEqualTo("relation:SIAMOIS.RELATION.ORDER:TARGET:RECORDING_UNIT");
        DefinitionRelation relation = DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT, binding).orElseThrow();
        assertThat(relation.code()).isEqualTo(ORDER);
        assertThat(relation.reading()).isEqualTo(DefinitionRelation.Reading.TARGET);
        assertThat(relation.resourceType()).isEqualTo("recording-units");
        assertThat(relation.qualified()).isTrue();
    }

    @Test
    void theSourceReadingListsTheTargetsOfTheLinksTheOwnerIsTheSourceOf() {
        String sql = parse(DefinitionRelation.Reading.SOURCE).pairsSql();

        assertThat(sql).contains("l.fk_source_recording_unit_id AS owner_id", "l.fk_target_recording_unit_id AS target_id",
                "d.code = 'SIAMOIS.RELATION.ORDER'", "l.fk_source_type_concept_id AS concept_id", "CAST('source' AS text) AS role");
        assertThat(sql).doesNotContain("UNION");
    }

    @Test
    void theTargetReadingIsTheMirror_theOwnerIsTheTargetAndTheValuesTheSources() {
        String sql = parse(DefinitionRelation.Reading.TARGET).pairsSql();

        assertThat(sql).contains("l.fk_target_recording_unit_id AS owner_id", "l.fk_source_recording_unit_id AS target_id",
                "l.fk_target_type_concept_id AS concept_id", "CAST('target' AS text) AS role");
    }

    @Test
    void aSymmetricRelationIsReadFromEitherEnd() {
        String sql = parse(DefinitionRelation.Reading.BOTH).pairsSql();

        assertThat(sql).contains(" UNION ALL ");
        assertThat(sql.split(" UNION ALL ")).hasSize(2);
    }

    @Test
    void theUncertainAttributeIsReadFromTheLinksOwnAnswers() {
        assertThat(parse(DefinitionRelation.Reading.SOURCE).pairsSql())
                .contains("u.value AS uncertain", "fca.fk_relation_link_id", "a.fk_custom_field_id = -900");
    }

    @Test
    void aPlainBindingIsNotARelation() {
        assertThat(DefinitionRelation.isBinding("parents")).isFalse();
        assertThat(DefinitionRelation.isBinding(null)).isFalse();
        assertThat(DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT, "parents")).isEmpty();
    }

    @Test
    void aMalformedBindingIsRefused() {
        assertThat(DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT, "relation:ORDER:SOURCE")).isEmpty();
        assertThat(DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT, "relation:ORDER:SIDEWAYS:RECORDING_UNIT")).isEmpty();
        assertThat(DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT, "relation:ORDER:SOURCE:WIDGET")).isEmpty();
        assertThat(DefinitionRelation.parse(null, "relation:ORDER:SOURCE:RECORDING_UNIT")).isEmpty();
    }

    @Test
    void aCodeThatCouldBreakOutOfTheSqlIsRefused() {
        assertThat(DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT,
                "relation:X' OR '1'='1:SOURCE:RECORDING_UNIT")).isEmpty();
        assertThat(DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT,
                "relation:A;DROP TABLE x:SOURCE:RECORDING_UNIT")).isEmpty();
    }

    @Test
    void aSymmetricReadingNeedsTheSameKindOnBothEnds() {
        assertThat(DefinitionRelation.parse(RelationEntityKind.SPECIMEN, "relation:X:BOTH:RECORDING_UNIT")).isEmpty();
    }

    @Test
    void aPersonIsOnlyEverAFarEnd() {
        assertThat(DefinitionRelation.parse(RelationEntityKind.PERSON, "relation:X:SOURCE:RECORDING_UNIT")).isEmpty();
        assertThat(DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT, "relation:X:SOURCE:PERSON")).isPresent();
        assertThat(DefinitionRelation.parse(RelationEntityKind.PERSON, "relation:X:TARGET:RECORDING_UNIT")).isPresent();
    }

    @Test
    void theFarKindDecidesWhereTheTargetsAreRead() {
        DefinitionRelation toPerson = DefinitionRelation.parse(RelationEntityKind.RECORDING_UNIT,
                "relation:X:SOURCE:PERSON").orElseThrow();

        assertThat(toPerson.targetTable()).isEqualTo("person");
        assertThat(toPerson.targetKey()).isEqualTo("person_id");
        assertThat(toPerson.resourceType()).isEqualTo("persons");
        assertThat(toPerson.pairsSql()).contains("l.fk_target_person_id AS target_id");
    }

    @Test
    void relationSourcesFindsBothKinds() {
        assertThat(RelationSources.of(RecordingUnit.class, "parents")).containsSame(RelationField.RECORDING_UNIT_PARENTS);
        assertThat(RelationSources.of(RecordingUnit.class, "relation:X:SOURCE:RECORDING_UNIT"))
                .get().isInstanceOf(DefinitionRelation.class);
        assertThat(RelationSources.isRelation(RecordingUnit.class, "relation:X:SOURCE:RECORDING_UNIT")).isTrue();
        assertThat(RelationSources.isRelation(RecordingUnit.class, "fullIdentifier")).isFalse();
        assertThat(RelationSources.isRelation(RecordingUnit.class, null)).isFalse();
    }

    @Test
    void anOwnerOfAnotherKindIsNotMistakenForTheFar() {
        // a phase can own a relation to recording units; a binding read for a class we cannot map is not a relation
        assertThat(RelationSources.of(Phase.class, "relation:X:SOURCE:RECORDING_UNIT")).isPresent();
        assertThat(RelationSources.of(String.class, "relation:X:SOURCE:RECORDING_UNIT")).isEmpty();
    }
}
