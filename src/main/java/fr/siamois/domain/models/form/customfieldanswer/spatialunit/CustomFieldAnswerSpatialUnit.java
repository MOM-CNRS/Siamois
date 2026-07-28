package fr.siamois.domain.models.form.customfieldanswer.spatialunit;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.*;

import java.util.List;
import java.util.Objects;

@Entity
public abstract class CustomFieldAnswerSpatialUnit extends CustomFieldAnswer {

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "custom_field_answer_spatial_unit_answers",
            joinColumns = {@JoinColumn(name = "fk_custom_field_id", referencedColumnName = "fk_custom_field_id"),
                          @JoinColumn(name = "fk_form_config_answer_id", referencedColumnName = "fk_form_config_answer_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_spatial_unit_id")})
    protected List<SpatialUnit> spatialUnits;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CustomFieldAnswerSpatialUnit that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(spatialUnits, that.spatialUnits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), spatialUnits);
    }
}
