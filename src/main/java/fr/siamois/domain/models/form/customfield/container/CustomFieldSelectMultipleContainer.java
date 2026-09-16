package fr.siamois.domain.models.form.customfield.container;

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
@DiscriminatorValue("SELECT_MULTIPLE_CONTAINER")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectMultipleContainer extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-box-seam";
    }

    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/containerMultiple.xhtml";
    }

}
