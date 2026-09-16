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
@DiscriminatorValue("SELECT_MULTIPLE_SPATIAL_UNIT_TREE")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectMultipleSpatialUnitTree extends CustomField {

    private String source;

    @Override
    public String getIcon() {
        return "bi bi-geo-alt";
    }

    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/spatialMultiple.xhtml";
    }

}
