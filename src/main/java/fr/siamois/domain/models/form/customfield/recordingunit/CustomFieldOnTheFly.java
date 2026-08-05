package fr.siamois.domain.models.form.customfield.recordingunit;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.Entity;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor
public abstract class CustomFieldOnTheFly extends CustomField {
}
