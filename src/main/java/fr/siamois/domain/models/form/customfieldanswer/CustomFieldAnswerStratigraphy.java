package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.dto.StratigraphicRelationshipDTO;
import fr.siamois.dto.entity.RecordingUnitSummaryDTO;
import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
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
