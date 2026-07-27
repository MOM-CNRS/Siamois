package fr.siamois.domain.models.form.customfieldanswer.spatialunit;

import fr.siamois.domain.models.spatialunit.SpatialUnit;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;


@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_SPATIAL_UNIT_TREE")
public class CustomFieldAnswerSelectMultipleSpatialUnitTree extends CustomFieldAnswerSpatialUnit {
    @Override
    public Object getValue() {
        return spatialUnits;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setValue(Object value) {
        if (Objects.isNull(spatialUnits)) spatialUnits = new ArrayList<>();
        spatialUnits.clear();
        if  (Objects.isNull(value)) return;
        if (value instanceof Collection collection) {
            spatialUnits.addAll(collection);
        } else if (value instanceof SpatialUnit spatialUnit) {
            spatialUnits.add(0, spatialUnit);
        } else {
            throw new IllegalArgumentException(String.format("value is not a Collection or SpatialUnit value: %s", value));
        }
    }
}
