package fr.siamois.domain.models.form.customfield.actionunit;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@SuperBuilder
@Entity
@DiscriminatorValue("SELECT_ONE_ACTION_CODE")
public class CustomFieldSelectOneActionCode extends CustomField {
    @Override
    public String getIcon() {
        return "bi bi-qr-code";
    }

    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/actionCode.xhtml";
    }
}
