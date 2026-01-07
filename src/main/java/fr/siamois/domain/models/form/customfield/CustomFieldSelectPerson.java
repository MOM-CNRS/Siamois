package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@NoArgsConstructor
@SuperBuilder
public class CustomFieldSelectPerson extends CustomField {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomFieldSelectPerson that = (CustomFieldSelectPerson) o;
        return super.equals(that);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
