package fr.siamois.domain.models.form.customfield.basetypes;

import fr.siamois.domain.models.vocabulary.Concept;
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

    /** The geometry system field of a table: its fixed id and its concept, bound to the record's {@code geom}. */
    public static CustomFieldGeometry of(long id, Concept concept) {
        CustomFieldGeometry field = new CustomFieldGeometry();
        field.setId(id);
        field.setLabel("common.field.geometry");
        field.setIsSystemField(true);
        field.setIsTextArea(true);
        field.setValueBinding("geom");
        field.setConcept(concept);
        return field;
    }

    @Override
    public String getIcon() {
        return "bi bi-bounding-box-circles";
    }

}
