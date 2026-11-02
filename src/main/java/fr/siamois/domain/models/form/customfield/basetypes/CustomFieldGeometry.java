package fr.siamois.domain.models.form.customfield.basetypes;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * A record's geometry, shown as its WKT in the SRID it carries. It is displayed by the form, never edited
 * through it: the geometry comes from the API or an import. A text field otherwise, so everything that reads
 * a text value (lists, exports) reads its WKT.
 */
@Entity
@DiscriminatorValue("GEOMETRY")
@NoArgsConstructor
@Table(name = "custom_field")
@SuperBuilder
public class CustomFieldGeometry extends CustomFieldText {

    @Override
    public String getIcon() {
        return "bi bi-bounding-box-circles";
    }

}
