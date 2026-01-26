package fr.siamois.utils.stratigraphy;

import fr.siamois.domain.models.exceptions.stratigraphy.StratigraphicUnitNotFoundInAnyGroup;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.services.recordingunit.StratigraphicRelationshipService;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Data
public class SynchronousGroupBuilder {


    // Stratigraphic unit list and their relationship
    private final List<RecordingUnit> recordingUnits;

    private long[] maitreES;
    private final String[] saiUstatut; // statut de l'US (Fait, MES, US simple par défaut)
    private long[] enSynch;
    private List<String> collecComm;

    // The list of syncronous group to return
    private List<SynchronousGroup> synchronousGroupList = new ArrayList<>();


    public SynchronousGroupBuilder(List<RecordingUnit> recordingUnits, String[] saiUstatut, long[] enSynch, List<String> collecComm) {
        this.recordingUnits = recordingUnits;

        this.saiUstatut = saiUstatut;
        this.collecComm = collecComm;
        this.enSynch = enSynch;

        // Store the synchronous group master index
        maitreES = new long[recordingUnits.size()];

    }

    public boolean findTransitiveRel(RecordingUnit unit1, int u2) {

        RecordingUnit unit2 = recordingUnits.get(u2);
        boolean signalModif = false;

        // Déduction par transitivité
        for (RecordingUnit recordingUnit : recordingUnits) {
            if ((hasSynchronousRelationship(unit2, recordingUnit) ||
                    hasSynchronousRelationship(recordingUnit, unit2)) &&
                    !hasSynchronousRelationship(unit1, recordingUnit)) {

                //  The following lines are the equivalent of "MSyn(u, u3) = Synchro;"
                StratigraphicRelationship newRelationship = new StratigraphicRelationship();
                newRelationship.setUnit1(unit1);
                newRelationship.setUnit2(recordingUnit);
                newRelationship.setConcept(StratigraphicRelationshipService.SYNCHRONOUS); // Assuming this is your synchronous concept
                unit1.getRelationshipsAsUnit1().add(newRelationship);
                //  End
                signalModif = true;
            }
        }

        return signalModif;
    }

    public boolean iterateAndFindSynchronismsWithGivenUnit(int u, boolean[][] coche ) {

        RecordingUnit unit = recordingUnits.get(u);
        boolean signalModifSymetry = false;
        boolean signalModifTransitivity = false;

        for (int u2 = 0; u2 < recordingUnits.size(); u2++) {

            if (hasSynchronousRelationship(unit, recordingUnits.get(u2)) && u != u2 && !coche[u][u2]) {

                coche[u][u2] = true;

                // Déduction par symétrie
                if (!hasSynchronousRelationship(recordingUnits.get(u2), recordingUnits.get(u))) { // MSyn(u2, u) != Synchro
                    // The following lines are the equivalent of "MSyn(u2, u) = Synchro;"
                    StratigraphicRelationship newRelationship = new StratigraphicRelationship();
                    newRelationship.setUnit1(recordingUnits.get(u2));
                    newRelationship.setUnit2(unit);
                    newRelationship.setConcept(StratigraphicRelationshipService.SYNCHRONOUS); // Assuming this is your synchronous concept
                    recordingUnits.get(u2).getRelationshipsAsUnit1().add(newRelationship);
                    //  End
                    signalModifSymetry = true;
                }

                // We iterate over the whole list again to find synchronisms by transitivity
                signalModifTransitivity = findTransitiveRel(unit, u2);
            }
        }

        return signalModifTransitivity || signalModifSymetry;
    }

    public void findTransitiveAndReflexiveRelationships() {

        // Variables locales
        boolean signalModif;
        // coche keeps tracks of the visited cells of the synchronisms matrix / matrice signal de relation traitée (accération traitement)
        boolean[][] coche = new boolean[recordingUnits.size()][recordingUnits.size()];

        do {
            signalModif = false;
            for (int u = 0; u < recordingUnits.size(); u++) { // We are going to iterate among all the recording units
                if (enSynch[u] > 0) { // If the current US is part of a synchronous group
                    // We compare the current recordingUnits (u) with every other recordingUnits (u2) to find synchronisms
                    signalModif = iterateAndFindSynchronismsWithGivenUnit(u, coche);
                }
            }
        } while (signalModif);
    }

    public void removeSynchronousRelBetweenUnitAndItself(int unitIndex) {
        // Remove relationship between u2 and itself so it cant be detected anymore as a group
        if (hasSynchronousRelationship(
                recordingUnits.get(unitIndex),
                recordingUnits.get(unitIndex)
        )) {
            // Remove self-referential synchronous relationship
            recordingUnits.get(unitIndex).getRelationshipsAsUnit1().removeIf(
                    rel -> rel.getUnit2().equals(recordingUnits.get(unitIndex)) && isSynchronous(rel)
            );
        }
    }

    public void findAndAddUnitToSynchronousGroup(SynchronousGroup group, int firstUnitIndex) {

        for (int u2 = 0; u2 < recordingUnits.size(); u2++) {
            if (hasSynchronousRelationship(
                    recordingUnits.get(firstUnitIndex),
                    recordingUnits.get(u2)
            )) {

                enSynch[u2] = firstUnitIndex; // Indique la 1ère US de l'ensemble synchrone
                group.addUnit(recordingUnits.get(u2));

                if (saiUstatut[u2].equals("maître d'ES")) {
                    // If we declared u2 as a group master and u is also a group mastr
                    // , we have a problem because there already is a group master
                    if (maitreES[firstUnitIndex] == 0) {
                        group.setMaster(recordingUnits.get(u2));
                    } else {
                        collecComm.add("Attention : plusieurs maîtres pour l'ensemble synchrone " + firstUnitIndex);
                    }
                }

                removeSynchronousRelBetweenUnitAndItself(u2) ;

            }
        }
    }

    public SynchronousGroup findGroupContainingUnit(RecordingUnit unit) {
        for (SynchronousGroup group : synchronousGroupList) {
            if (group.contains(unit)) {
                return group;
            }
        }
        return null; // Return null if no group contains the unit
    }

    public boolean containsRelationship(Set<StratigraphicRelationship> relationships,
                                        RecordingUnit unit1,
                                        RecordingUnit unit2,
                                        Concept type) {
        return relationships.stream()
                .anyMatch(rel -> rel.getUnit1().equals(unit1)
                        && rel.getUnit2().equals(unit2)
                        && rel.getConcept().equals(type));
    }

    public void transferRelationshipToGroup(StratigraphicRelationship rel, SynchronousGroup group) {
        StratigraphicRelationship newRel = new StratigraphicRelationship();
        // Unit1 will be the current group
        newRel.setUnit1(group);
        // Find the group containing unit2
        SynchronousGroup group2 = findGroupContainingUnit(rel.getUnit2());
        if(group2 == null) {
            throw new StratigraphicUnitNotFoundInAnyGroup("Impossible to find unit2 in groups");
        }
        newRel.setUnit2(group2);
        newRel.setConcept(rel.getConcept());
        if(!containsRelationship(group.getRelationshipsAsUnit1(), newRel.getUnit1(), newRel.getUnit2(), newRel.getConcept())) {
            group.getRelationshipsAsUnit1().add(newRel);
        }
    }


    public void transferRelationshipsFromUnitsToGroup() {

        // We iterate over each group to assign the relationships of its slaves to the group
        for(SynchronousGroup group : synchronousGroupList) {
            for(RecordingUnit unit : group.getUnits()) {
                for(StratigraphicRelationship rel : unit.getRelationshipsAsUnit1()) {
                    // We will transfer asynchronous rel to the parent and uncertain synchronous rel
                    if(rel.getConcept().equals(StratigraphicRelationshipService.ASYNCHRONOUS)) {
                        transferRelationshipToGroup(rel, group);
                    }
                }
            }
        }
    }

    // La méthode qui simule la logique de "EnsemblesSynchrones" en Java
    // constitution des ensembles synchrones certains
    // déduction des synchronismes

    public void build() {

        // Initialisation des tableaux
        Arrays.fill(maitreES, 0);

        findTransitiveAndReflexiveRelationships();

        // Traitement des ensembles synchrones
        for (int u = 0; u < recordingUnits.size(); u++) {

            if (hasSynchronousRelationship(
                    recordingUnits.get(u),
                    recordingUnits.get(u)
            )) { // relation réflexive indiquant un ensemble synchrone

                SynchronousGroup newGroup = new SynchronousGroup();
                newGroup.addUnit(recordingUnits.get(u));
                newGroup.setMaster(recordingUnits.get(u));

                findAndAddUnitToSynchronousGroup(newGroup, u);


                // Add the group to the list of group
                synchronousGroupList.add(newGroup);
            }
        }

        // Add units that are not in any group
        for (RecordingUnit unit : recordingUnits) {
            boolean isInGroup = synchronousGroupList.stream()
                    .anyMatch(group -> group.contains(unit));

            if (!isInGroup) {
                SynchronousGroup singleUnitGroup = new SynchronousGroup();
                singleUnitGroup.addUnit(unit);
                singleUnitGroup.setMaster(unit); // The unit itself is its own master
                synchronousGroupList.add(singleUnitGroup);
            }
        }

        transferRelationshipsFromUnitsToGroup();
    }

    /**
     * Checks if unit1 as a synchronous relationship with unit2
     * (Equivalent of (MSyn(U, U2) == Synchro) in Stratifiant)
     *
     * @param unit1 The first RecordingUnit.
     * @param unit2 The second RecordingUnit.
     * @return true if they are in a synchronous relationship, false otherwise.
     */
    private boolean hasSynchronousRelationship(RecordingUnit unit1, RecordingUnit unit2) {
        return unit1.getRelationshipsAsUnit1().stream()
                .anyMatch(rel -> rel.getUnit2().equals(unit2) && isSynchronous(rel));
    }

    /**
     * Determines if a given relationship is synchronous.
     *
     * @param relationship The StratigraphicRelationship to check.
     * @return true if it represents a synchronous relationship.
     */
    private boolean isSynchronous(StratigraphicRelationship relationship) {

        return StratigraphicRelationshipService.SYNCHRONOUS.equals(relationship.getConcept());
    }

}
