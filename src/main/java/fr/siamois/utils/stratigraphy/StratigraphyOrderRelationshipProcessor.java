package fr.siamois.utils.stratigraphy;

import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.recordingunit.StratigraphicRelationshipService;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Data
public class StratigraphyOrderRelationshipProcessor {

    // Stratigraphic unit list and their relationship
    private final List<SynchronousGroup> groupList;


    private boolean signalConflict = false;
    private List<List<SynchronousGroup>> loops;

    public StratigraphyOrderRelationshipProcessor(List<SynchronousGroup> groupList) {

        this.groupList = groupList;
        this.loops = new ArrayList<>();



    }

    public boolean containsRelationshipOfType(Set<StratigraphicRelationship> relationships,
                                              Concept type) {
        return relationships.stream()
                .anyMatch(rel -> rel.getConcept().equals(type));
    }

    public boolean hasRelationshipWithUnit2OfType(SynchronousGroup unit1, SynchronousGroup unit2, Concept type) {
        return unit1.getRelationshipsAsUnit1().stream()
                .anyMatch(rel -> rel.getUnit2().equals(unit2) && rel.getConcept().equals(type));
    }

    public boolean hasRelationshipWithUnit2(SynchronousGroup unit1, SynchronousGroup unit2) {
        return unit1.getRelationshipsAsUnit1().stream()
                .anyMatch(rel -> rel.getUnit2().equals(unit2));
    }

    public Optional<StratigraphicRelationship> getRelationshipWithUnit2(SynchronousGroup unit1, SynchronousGroup unit2) {
        return unit1.getRelationshipsAsUnit1().stream()
                .filter(rel -> rel.getUnit2().equals(unit2))
                .findFirst(); // Returns an Optional containing the first match, or empty if none found
    }

    private boolean deductTransitiveRelationshipBetweenU1AndOthersThroughU2(SynchronousGroup unit1, SynchronousGroup unit2) {
        boolean reiterate = false;
        for (SynchronousGroup group3 : groupList) {
            // We look for rels between group 2 and group 3
            if (
                    hasRelationshipWithUnit2(unit2, group3) &&
                            !hasRelationshipWithUnit2OfType( // if the relationship has not been deducted yet
                                    unit1, group3, StratigraphicRelationshipService.ASYNCHRONOUS_DEDUCTED
                            )
            ) {
                // If no relationships at all
                if (!hasRelationshipWithUnit2(
                        unit1, group3
                )) {
                    reiterate = true;
                }
                // modify or create the rel so it is marked as deducted
                Optional<StratigraphicRelationship> rel = getRelationshipWithUnit2(unit1, group3);
                if (rel.isPresent()) {
                    // type is now asynchronous deducted
                    rel.get().setConcept(StratigraphicRelationshipService.ASYNCHRONOUS_DEDUCTED);
                } else {
                    // we add it as asynchronous deducted
                    StratigraphicRelationship newRel = new StratigraphicRelationship();
                    newRel.setUnit1(unit1);
                    newRel.setUnit2(group3);
                    newRel.setConcept(StratigraphicRelationshipService.ASYNCHRONOUS_DEDUCTED);
                    unit1.getRelationshipsAsUnit1().add(newRel);
                }

            }
        }
        return reiterate;
    }


    public Boolean processUnitRelationships(SynchronousGroup group1, boolean[] coche) {

        boolean reiterate = false;

        for (int u2 = 0; u2 < groupList.size(); u2++) {
            SynchronousGroup group2 = groupList.get(u2);
            // If group2 has not been processed and there is a relationship between group1 and group2
            if (!coche[u2] && hasRelationshipWithUnit2(group1, group2)) {
                coche[u2] = true;

                // Check for reflexive relationship
                if (group1 == group2) {
                    this.signalConflict = true;
                } else {
                    reiterate = deductTransitiveRelationshipBetweenU1AndOthersThroughU2(group1, group2);
                }
            }
        }

        return reiterate;
    }

    public void deductRelationshipByTransitivity() {

        // dedeucation par transitivité de toute les realtions redondantes et non redondantes
        for (SynchronousGroup group : groupList) {
            // If the group has certain asynch relationships
            if (containsRelationshipOfType(group.getRelationshipsAsUnit1(), StratigraphicRelationshipService.ASYNCHRONOUS)) {
                boolean[] coche = new boolean[groupList.size()]; // reinit marks
                boolean reiterate;
                do {
                    reiterate = processUnitRelationships(group, coche);
                }
                while (reiterate);
            }
        }
    }

    public void loopsDetection() {
        for (SynchronousGroup group : groupList) {
            if (hasRelationshipWithUnit2(group, group)) {
                // loop detected
                List<SynchronousGroup> newLoop = new ArrayList<>();
                loops.add(newLoop);
                newLoop.add(group);
                for (SynchronousGroup group2 : groupList) {
                    if (hasRelationshipWithUnit2(group2, group) && hasRelationshipWithUnit2(group, group2)) {
                        // 'suivi du circuit par les rel. symétriques
                        newLoop.add(group2);
                        // Remove the reflexive relationship
                        group2.getRelationshipsAsUnit1().removeIf(rel -> rel.getUnit2().equals(group2));
                    }
                }
            }
        }
    }

    public void process() {

        deductRelationshipByTransitivity();

        if (signalConflict) {
            loopsDetection();
        }

    }
}
