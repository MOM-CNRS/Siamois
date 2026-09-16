package fr.siamois.domain.models.form.customfield.actionunit;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_ONE_ACTION_UNIT")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectOneActionUnit extends CustomField {
    @Override
    public String getIcon() {
        return "bi bi-arrow-down-square";
    }

    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/actionUnit.xhtml";
    }

}
