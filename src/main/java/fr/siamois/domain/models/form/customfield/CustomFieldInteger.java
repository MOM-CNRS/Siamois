package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@DiscriminatorValue("INTEGER")
@Table(name = "custom_field")
public class CustomFieldInteger extends CustomField {

    @Column(name = "min_value")
    private Integer minValue = Integer.MIN_VALUE;

    @Column(name = "max_value")
    private Integer maxValue = Integer.MAX_VALUE;

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
