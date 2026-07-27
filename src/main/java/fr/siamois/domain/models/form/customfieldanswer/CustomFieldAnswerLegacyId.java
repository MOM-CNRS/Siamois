package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customformresponse.CustomFormResponse;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@Deprecated(forRemoval = true, since = "0.13.2-SNAPSHOT")
public class CustomFieldAnswerLegacyId implements Serializable {

    @ManyToOne
    @JoinColumn(name = "fk_field_id", nullable = false)
    private  CustomField field;

    @ManyToOne
    @JoinColumn(name = "fk_form_response", nullable = false)
    private  CustomFormResponse formResponse;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerLegacyId that)) return false;

        return Objects.equals(field, that.field) &&
                Objects.equals(formResponse, that.formResponse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, formResponse);
    }

}
