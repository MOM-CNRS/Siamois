package fr.siamois.domain.models.form.customfield.basetypes;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Objects;


@Getter
@Setter
@Entity
@DiscriminatorValue("DATETIME")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldDateTime extends CustomField {

    private Boolean showTime;

    private LocalDateTime min;

    private LocalDateTime max;

    @Override
    public String getIcon() {
        return "bi bi-calendar";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CustomFieldDateTime that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(showTime, that.showTime) && Objects.equals(min, that.min) && Objects.equals(max, that.max);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), showTime, min, max);
    }
}
