package fr.siamois.domain.models.form.customfieldanswer.spatialunit;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.*;

import java.util.List;

@Entity
public abstract class CustomFieldAnswerSpatialUnit extends CustomFieldAnswer {

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "custom_field_answer_spatial_unit_answers",
            joinColumns = {@JoinColumn(name = "fk_field_answer_id")},
            inverseJoinColumns = {@JoinColumn(name = "fk_spatial_unit_id")})
    protected List<SpatialUnit> spatialUnits;

}
