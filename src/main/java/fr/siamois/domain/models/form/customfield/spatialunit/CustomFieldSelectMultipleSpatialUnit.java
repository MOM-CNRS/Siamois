package fr.siamois.domain.models.form.customfield.spatialunit;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Several places, picked from a flat search (a document's linked places). It behaves exactly like
 * {@link CustomFieldSelectMultipleSpatialUnitTree} — which it extends, so every check on the tree
 * variant covers it — but cannot share its discriminator: that one is longer than the 31 characters
 * of {@code custom_field.answer_type}, so it cannot be persisted as a system field.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_SPATIAL_UNIT")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectMultipleSpatialUnit extends CustomFieldSelectMultipleSpatialUnitTree {
}
