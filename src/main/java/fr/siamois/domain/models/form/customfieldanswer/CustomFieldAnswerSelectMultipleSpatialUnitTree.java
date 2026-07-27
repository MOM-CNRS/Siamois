package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;



@Data
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_SPATIAL_UNIT_TREE")
@Table(name = "custom_field_answer")
public class CustomFieldAnswerSelectMultipleSpatialUnitTree extends CustomFieldAnswerLegacy {

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "custom_field_answer_spatial_unit_answers",
            joinColumns = {
                    @JoinColumn(name = "fk_field_id", referencedColumnName = "fk_field_id"),
                    @JoinColumn(name = "fk_form_response", referencedColumnName = "fk_form_response")
            },
            inverseJoinColumns = { @JoinColumn(name = "fk_spatial_unit") }
    )
    private Set<SpatialUnit> value = new HashSet<>();


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomFieldAnswerSelectMultipleSpatialUnitTree that)) return false;
        if (!super.equals(o)) return false; // Ensures any inherited fields are compared

        return Objects.equals(getPk(), that.getPk());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getPk());
    }

}
