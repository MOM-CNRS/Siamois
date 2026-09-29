package fr.siamois.domain.models.form.customfield.recordingunit;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A recording unit's stratigraphic relationships, as one field: every other unit it is related
 * to, each qualified by the relationship (its concept, which side the unit is on, whether it is
 * synchronous or uncertain). Recording units only — a system field of their details form, never an
 * additional one; read-only through the unit's answers, since a relationship is more than a target.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_STRATIGRAPHY")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldStratigraphicRelationships extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-layers";
    }

}
