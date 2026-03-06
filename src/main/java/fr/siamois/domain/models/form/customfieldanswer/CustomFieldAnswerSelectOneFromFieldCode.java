package fr.siamois.domain.models.form.customfieldanswer;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
