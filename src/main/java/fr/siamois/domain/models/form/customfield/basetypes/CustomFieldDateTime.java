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
import java.time.Month;


@Getter
@Setter
@Entity
@DiscriminatorValue("DATETIME")
@Table(name = "custom_field")
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class CustomFieldDateTime extends CustomField {

    private Boolean showTime;

    private LocalDateTime min = LocalDateTime.of(1000, Month.JANUARY, 1, 1, 1);

    private LocalDateTime max = LocalDateTime.of(9999, Month.DECEMBER, 31, 23, 59);

    @Override
    public String getIcon() {
        return "bi bi-calendar";
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
