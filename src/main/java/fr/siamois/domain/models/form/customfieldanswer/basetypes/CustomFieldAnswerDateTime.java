package fr.siamois.domain.models.form.customfieldanswer.basetypes;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Objects;


@Data
@Entity
@DiscriminatorValue("DATETIME")
public class CustomFieldAnswerDateTime extends CustomFieldAnswer {

    @Column(name = "value_as_datetime")
    private LocalDateTime value;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CustomFieldAnswerDateTime that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public void setValue(Object value) {
        if(value instanceof LocalDateTime localDateTime) {
            this.value = localDateTime;
        } else {
            throw new IllegalArgumentException("Value must be a LocalDateTime");
        }
    }
}
