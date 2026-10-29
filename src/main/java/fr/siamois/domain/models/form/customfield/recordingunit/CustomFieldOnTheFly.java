package fr.siamois.domain.models.form.customfield.recordingunit;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A field a user adds to one record's fiche ("Ajouter une mesure"): it belongs to that record alone,
 * through the record's {@code onTheFlyFields} link table. Measurement is the first kind.
 */
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
public abstract class CustomFieldOnTheFly extends CustomField {

    /** The panel of the fiche the field was added to; null shows it in the fiche's last panel. */
    @Column(name = "panel_name")
    private String panelName;
}
