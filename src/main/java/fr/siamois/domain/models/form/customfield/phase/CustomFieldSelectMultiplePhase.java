package fr.siamois.domain.models.form.customfield.phase;

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
@DiscriminatorValue("SELECT_MULTIPLE_PHASE")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectMultiplePhase extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-layers";
    }

    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/phaseMultiple.xhtml";
    }
}
