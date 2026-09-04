package fr.siamois.domain.models.form.customfield.basetypes;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@DiscriminatorValue("DECIMAL")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CustomFieldDecimal extends CustomField {

    @Column(name = "min_value_decimal")
    private Double minValue = -Double.MAX_VALUE;

    @Column(name = "max_value_decimal")
    private Double maxValue = Double.MAX_VALUE;

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String getIcon() {
        return "bi bi-123";
    }

}
