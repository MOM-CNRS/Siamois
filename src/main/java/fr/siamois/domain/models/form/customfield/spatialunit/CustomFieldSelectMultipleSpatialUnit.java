package fr.siamois.domain.models.form.customfield.spatialunit;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Several places, picked from a flat search (a document's linked places). The tree variant
 * ({@link CustomFieldSelectMultipleSpatialUnitTree}) cannot be persisted as a system field: its
 * discriminator is longer than the 31 characters of {@code custom_field.answer_type}.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_SPATIAL_UNIT")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectMultipleSpatialUnit extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-geo-alt";
    }

}
