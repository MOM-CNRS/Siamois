package fr.siamois.domain.models.form.customfieldanswer.spatialunit;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Objects;


@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue("SELECT_ONE_SPATIAL_UNIT")
public class CustomFieldAnswerSelectOneSpatialUnit extends CustomFieldAnswerSpatialUnit {

    @Override
    public Object getValue() {
        if (Objects.isNull(spatialUnits) || spatialUnits.isEmpty()) {
            return null;
        }
        return spatialUnits.get(0);
    }

    @Override
    public void setValue(Object value) {
        if (Objects.isNull(spatialUnits)) spatialUnits = new ArrayList<>();
        spatialUnits.clear();
        if (value instanceof SpatialUnit spatialUnit) {
            spatialUnits.add(spatialUnit);
        } else {
            throw new IllegalArgumentException("Invalid value passed to spatial unit selection");
        }
    }
}
