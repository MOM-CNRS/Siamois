package fr.siamois.domain.models.form.customfield.basetypes;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


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
    // min_value / max_value are shared with CustomFieldMeasurement (Long) in the single custom_field
    // table, where they are bigint: map them as BIGINT so schema validation accepts both subclasses.
    @Builder.Default
    @Column(name = "min_value")
    @JdbcTypeCode(SqlTypes.BIGINT)
    private Integer minValue = Integer.MIN_VALUE;

    @Builder.Default
    @Column(name = "max_value")
    @JdbcTypeCode(SqlTypes.BIGINT)
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
