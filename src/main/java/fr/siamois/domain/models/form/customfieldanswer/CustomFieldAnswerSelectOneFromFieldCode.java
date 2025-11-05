package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Objects;


@Data
@Entity
@SuperBuilder    // <-- indispensable ici aussi
@NoArgsConstructor
@DiscriminatorValue("SELECT_ONE_FROM_FIELD_CODE")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectOneFromFieldCode extends CustomFieldAnswer {

    @ManyToOne
    @JoinColumn(name = "fk_value_as_concept")
    private Concept value;

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
