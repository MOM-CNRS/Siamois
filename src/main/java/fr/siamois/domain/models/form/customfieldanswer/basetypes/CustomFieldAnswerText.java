package fr.siamois.domain.models.form.customfieldanswer.basetypes;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerLegacy;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("TEXT")
public class CustomFieldAnswerText extends CustomFieldAnswer {

    @Column(name = "value_as_text")
    private String value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerText that)) return false;
        if (!super.equals(o)) return false; // Ensures inherited fields are compared

        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

}
