package fr.siamois.domain.models.form.customfield.person;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_PERSON")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectMultiplePerson extends CustomFieldSelectPerson {

    @Override
    public String getIcon() {
        return "bi bi-people";
    }

    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/personMultiple.xhtml";
    }

}
