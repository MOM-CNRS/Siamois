package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import jakarta.faces.context.FacesContext;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@DiscriminatorValue("STRATIGRAPHY")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerStratigraphy extends CustomFieldAnswer {

    // The rels
    private transient Set<StratigraphicRelationshipDTO> anteriorRelationships = new HashSet<>();
    private transient Set<StratigraphicRelationshipDTO> posteriorRelationships = new HashSet<>();
    private transient Set<StratigraphicRelationshipDTO> synchronousRelationships = new HashSet<>();

    // New rel form
    private transient ConceptAutocompleteDTO conceptToAdd;
    private transient RecordingUnitSummaryDTO sourceToAdd = new RecordingUnitSummaryDTO(); // always the recording unit the panel is about
    private transient RecordingUnitSummaryDTO targetToAdd;
    private transient Boolean vocabularyDirectionToAdd = false; // always false in this version
    private transient Boolean isUncertainToAdd;

    // Displayed selected rel info
    private transient StratigraphicRelationshipDTO selectedRel;


    public void updateSelectedRel() {


        // Retrieve parameters from the request
        String sourceId = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("source");
        String targetId = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("target");
        String typeRel = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("typeRel");

        if (sourceId == null || targetId == null || typeRel == null) {
            return;
        }

        // Determine which set to search based on typeRel
        Set<StratigraphicRelationshipDTO> relsToSearch = null;
        switch (typeRel.toLowerCase()) {
            case "synchronous":
                relsToSearch = synchronousRelationships;
                break;
            case "anterior":
                relsToSearch = anteriorRelationships;
                break;
            case "posterior":
                relsToSearch = posteriorRelationships;
                break;
            default:
                return;
        }

        // Search for the relationship
        for (StratigraphicRelationshipDTO rel : relsToSearch) {
            RecordingUnitSummaryDTO relUnit1 = rel.getUnit1();
            RecordingUnitSummaryDTO relUnit2 = rel.getUnit2();

            // Check if the source and target match (order doesn't matter)
            boolean sourceMatches = (relUnit1.getFullIdentifier().equals(sourceId) && relUnit2.getFullIdentifier().equals(targetId)) ||
                    (relUnit1.getFullIdentifier().equals(targetId) && relUnit2.getFullIdentifier().equals(sourceId));

            if (sourceMatches) {
                setSelectedRel(rel);
                setHasBeenModified(true);
                return;
            }
        }
    }

    public void deleteStratigraphicRelationship() {
        // Check if the relationship exists in anteriorRelationships
        if (anteriorRelationships!= null) {
            anteriorRelationships.removeIf(rel ->
                    rel.getUnit1().getFullIdentifier().equals(selectedRel.getUnit1().getFullIdentifier()) &&
                            rel.getUnit2().getFullIdentifier().equals(selectedRel.getUnit2().getFullIdentifier())
            );
        }

        // Check if the relationship exists in posteriorRelationships
        if (posteriorRelationships != null) {
            posteriorRelationships.removeIf(rel ->
                    rel.getUnit1().getFullIdentifier().equals(selectedRel.getUnit1().getFullIdentifier()) &&
                            rel.getUnit2().getFullIdentifier().equals(selectedRel.getUnit2().getFullIdentifier())
            );
        }

        // Check if the relationship exists in synchronousRelationships
        if (synchronousRelationships != null) {
            synchronousRelationships.removeIf(rel ->
                    rel.getUnit1().getFullIdentifier().equals(selectedRel.getUnit1().getFullIdentifier()) &&
                            rel.getUnit2().getFullIdentifier().equals(selectedRel.getUnit2().getFullIdentifier())
            );
        }

        selectedRel = null;
        setHasBeenModified(true);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerStratigraphy that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
