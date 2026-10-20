package fr.siamois.domain.models.form.customfield.recordingunit;

import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConceptFromFieldCode;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One side of a recording unit's stratigraphic relationships — the units anterior to it, the units
 * posterior to it, or its synchronous ones — each qualified by the relationship (its type concept,
 * whether it is uncertain). Recording units only: a system field of their details form, never an
 * additional one; read-only through the unit's answers, since a relationship is more than a target.
 * <p>
 * It is also a vocabulary field: the type of its relationships is a concept of the list its field
 * code names, or of the branch or collection a project restricts it to, exactly like any other
 * vocabulary field.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_STRATIGRAPHY")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldStratigraphicRelationships extends CustomFieldConceptFromFieldCode {

    @Override
    public String getIcon() {
        return "bi bi-layers";
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

}
