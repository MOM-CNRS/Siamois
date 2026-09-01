package fr.siamois.domain.models.form.customfield.basetypes;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@DiscriminatorValue("INTEGER")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CustomFieldInteger extends CustomField {

    // @Builder.Default is required here: without it, @SuperBuilder silently ignores this field
    // initializer and leaves minValue null whenever a builder call site doesn't set it explicitly
    // (this is what broke RecordingUnit's TPQ/TAQ fields - see issue #463).
    @Builder.Default
    @Column(name = "min_value")
    private Integer minValue = Integer.MIN_VALUE;

    @Builder.Default
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

    @Override
    public String getIcon() {
        return "bi bi-123";
    }

}
