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

/**
 * A yes/no field. By default it is a plain checkbox: unchecked reads as "no". With
 * {@link #allowNoAnswer} it has a third state, "not answered", which is a {@code null} answer.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("BOOLEAN")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CustomFieldBoolean extends CustomField {

    // @Builder.Default: without it @SuperBuilder ignores the initializer and leaves the column null.
    @Builder.Default
    @Column(name = "allow_no_answer")
    private Boolean allowNoAnswer = Boolean.FALSE;

    public boolean isNoAnswerAllowed() {
        return Boolean.TRUE.equals(allowNoAnswer);
    }

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
        return "bi bi-check-square";
    }

}
