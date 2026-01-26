package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.models.recordingunit.StratigraphicRelationship;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Objects;
import java.util.Set;


@Data
@Entity
@DiscriminatorValue("STRATIGRAPHY")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerStratigraphy extends CustomFieldAnswer {

    private transient Set<StratigraphicRelationship> anteriorRelationships;
    private transient Set<StratigraphicRelationship> posteriorRelationships;
    private transient Set<StratigraphicRelationship> synchronousRelationships;

    private transient Concept conceptToAdd;
    private transient RecordingUnit sourceToAdd; // always the recording unit the panel is about
    private transient RecordingUnit targetToAdd;
    private transient Boolean vocabularyDirectionToAdd; // always false in this version
    private transient Boolean isUncertainToAdd;

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
