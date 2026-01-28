package fr.siamois.domain.utils.stratigraphy.stratifiant;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.services.recordingunit.StratigraphicRelationshipService;
import fr.siamois.utils.stratigraphy.stratifiant.Stratifiant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StratifiantTest {

    @Test
    void loadStratifiantDataFromSpreadsheet_success() {

        List<RecordingUnit> expectedUnits = new ArrayList<>();
        RecordingUnit unit1 = new RecordingUnit();
        unit1.setFullIdentifier("US1");
        unit1.setId(0L);
        expectedUnits.add(unit1);
        RecordingUnit unit2 = new RecordingUnit();
        unit2.setFullIdentifier("US2");
        unit2.setId(1L);
        expectedUnits.add(unit2);
        RecordingUnit unit3 = new RecordingUnit();
        unit3.setFullIdentifier("US3");
        unit3.setId(2L);
        expectedUnits.add(unit3);
        RecordingUnit unit4 = new RecordingUnit();
        unit4.setFullIdentifier("fait1");
        expectedUnits.add(unit4);
        unit4.setId(3L);

        // add rels
        StratigraphicRelationship rel1 = new StratigraphicRelationship();
        rel1.setUnit1(unit1);
        rel1.setUnit2(unit3);
        rel1.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS);
        unit1.getRelationshipsAsUnit1().add(rel1);

        StratigraphicRelationship rel2 = new StratigraphicRelationship();
        rel2.setUnit1(unit2);
        rel2.setUnit2(unit4);
        rel2.setConcept(StratigraphicRelationshipService.SYNCHRONOUS);
        unit2.getRelationshipsAsUnit1().add(rel2);

        StratigraphicRelationship rel3 = new StratigraphicRelationship();
        rel3.setUnit1(unit3);
        rel3.setUnit2(unit4);
        rel3.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS);
        unit3.getRelationshipsAsUnit1().add(rel3);

        StratigraphicRelationship rel4 = new StratigraphicRelationship();
        rel4.setUnit1(unit4);
        rel4.setUnit2(unit2);
        rel4.setConcept(StratigraphicRelationshipService.SYNCHRONOUS);
        unit4.getRelationshipsAsUnit1().add(rel4);


        assertDoesNotThrow(() -> {
            List<RecordingUnit> units = Stratifiant.loadStratifiantDataFromSpreadsheet("src/test/resources/xlsx/stratifiant/loadDataTest.xlsx");
            // Assert
            assertEquals(4, units.size());
            assertEquals( expectedUnits.stream().map(RecordingUnit::getId).toList(),
                     units.stream().map(RecordingUnit::getId).toList()
            );
            assertEquals( expectedUnits.stream().map(RecordingUnit::getFullIdentifier).toList(),
                    units.stream().map(RecordingUnit::getFullIdentifier).toList()
            );
            assertEquals( expectedUnits.stream().map(RecordingUnit::getRelationshipsAsUnit1).toList(),
                    units.stream().map(RecordingUnit::getRelationshipsAsUnit1).toList()
            );
        });

    }
}