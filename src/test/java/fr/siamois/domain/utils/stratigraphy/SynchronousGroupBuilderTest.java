package fr.siamois.domain.utils.stratigraphy;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.services.recordingunit.StratigraphicRelationshipService;
import fr.siamois.utils.stratigraphy.SynchronousGroup;
import fr.siamois.utils.stratigraphy.SynchronousGroupBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronousGroupBuilderTest {

    @Test
    void testSynchronousGroupsFormation() {
        // Create Recording Units
        RecordingUnit unitA = new RecordingUnit(); unitA.setFullIdentifier("A");
        unitA.setRelationshipsAsUnit1(new HashSet<>()); unitA.setRelationshipsAsUnit2(new HashSet<>());
        RecordingUnit unitB = new RecordingUnit(); unitB.setFullIdentifier("B");
        unitB.setRelationshipsAsUnit1(new HashSet<>()); unitB.setRelationshipsAsUnit2(new HashSet<>());
        RecordingUnit unitC = new RecordingUnit(); unitC.setFullIdentifier("C");
        unitC.setRelationshipsAsUnit1(new HashSet<>()); unitC.setRelationshipsAsUnit2(new HashSet<>());
        RecordingUnit unitD = new RecordingUnit(); unitD.setFullIdentifier("D");
        unitD.setRelationshipsAsUnit1(new HashSet<>()); unitD.setRelationshipsAsUnit2(new HashSet<>());

        // Create Relationships (A ↔ B, A ↔ C)
        createSynchronousRelationship(unitA, unitB);
        createSynchronousRelationship(unitA, unitC);

        // Relationships that should be transferred to the synchronous group (A, B, C)
        createAsynchronousRelationship(unitA, unitD);
        createAsynchronousRelationship(unitB, unitD);

        // Initialize the builder
        List<RecordingUnit> units = List.of(unitA, unitB, unitC, unitD);
        List<String> collecComm = new ArrayList<>();
        long[] enSynch; // ensemble synchrone (ES) de l'US (O si pas en synchronisme)
        enSynch = IntStream.range(0, units.size()) // ensemble synchrone (ES) de l'US (O si pas en synchronisme)
                // Initialisation : each element get the value of its index because each US is in its on synchronous group
                .mapToLong(i -> i + 1) // Assigns index + 1 to each element
                .toArray();
        String[] saiUstatut = new String[units.size()];
        Arrays.fill(saiUstatut, "US");
        SynchronousGroupBuilder builder = new SynchronousGroupBuilder(units, saiUstatut, enSynch, collecComm);

        // Build synchronous groups
        builder.build();
        List<SynchronousGroup> groups = builder.getSynchronousGroupList();

        // Assertions
        assertEquals(2, groups.size(), "There should be 2 synchronous groups");

        // Find the group with multiple members
        SynchronousGroup multiUnitGroup = groups.stream()
                .filter(group -> group.getUnits().size() > 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a multi-unit synchronous group"));

        SynchronousGroup singleUnitGroup = groups.stream()
                .filter(group -> group.getUnits().size() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a single-unit synchronous group"));

        // Validate that there is a asynchronous relationship between the two groups
        assertEquals(1,multiUnitGroup.getRelationshipsAsUnit1().size());

        StratigraphicRelationship rel = multiUnitGroup.getRelationshipsAsUnit1().iterator().next();
        assertEquals(multiUnitGroup,rel.getUnit1());
        assertEquals(singleUnitGroup, rel.getUnit2());
        assertEquals(StratigraphicRelationshipService.ASYNCHRONOUS, rel.getConcept());


        // Validate multi-unit group
        assertTrue(multiUnitGroup.getUnits().containsAll(List.of(unitA, unitB, unitC)),
                "Multi-unit group should contain A, B, and C");

        // Validate single-unit group
        assertEquals(1, singleUnitGroup.getUnits().size(), "Single-unit group should have exactly 1 unit");
        assertTrue(singleUnitGroup.getUnits().contains(unitD), "Single-unit group should contain unit D");
    }

    private void createSynchronousRelationship(RecordingUnit unit1, RecordingUnit unit2) {
        StratigraphicRelationship relationship = new StratigraphicRelationship();
        relationship.setUnit1(unit1);
        relationship.setUnit2(unit2);
        relationship.setConcept(StratigraphicRelationshipService.SYNCHRONOUS);
        unit1.getRelationshipsAsUnit1().add(relationship);
    }
    private void createAsynchronousRelationship(RecordingUnit unit1, RecordingUnit unit2) {
        StratigraphicRelationship relationship = new StratigraphicRelationship();
        relationship.setUnit1(unit1);
        relationship.setUnit2(unit2);
        relationship.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS);
        unit1.getRelationshipsAsUnit1().add(relationship);
    }
}
