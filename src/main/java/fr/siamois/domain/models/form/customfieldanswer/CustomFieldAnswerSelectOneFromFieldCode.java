package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.LabelType;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("SELECT_ONE_FROM_FIELD_CODE")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectOneFromFieldCode extends CustomFieldAnswer {

    @ManyToOne
    @JoinColumn(name = "fk_value_as_concept")
    private Concept value;

    @Transient
    private ConceptLabel uiVal;

    public void setValue(ConceptLabel conceptLabel) {
        this.value = conceptLabel.getConcept();
    }

    public void setValue(Concept concept) {
        this.value = concept;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerSelectOneFromFieldCode that)) return false;
        if (!super.equals(o)) return false; // Ensures any inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
