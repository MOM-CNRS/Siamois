package fr.siamois.domain.models.form.customfield.spatialunit;

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
@DiscriminatorValue("SELECT_ADDRESS")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectOneAddress extends CustomField {
    @Override
    public String getIcon() {
        return "bi bi-mailbox";
    }

    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/address.xhtml";
    }
}
