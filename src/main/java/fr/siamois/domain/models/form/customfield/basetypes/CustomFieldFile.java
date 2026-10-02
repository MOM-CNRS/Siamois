package fr.siamois.domain.models.form.customfield.basetypes;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A stored file (a document's). It is shown and replaced through the upload endpoints, never written
 * by a form answer: the file's bytes do not travel in a PATCH.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("FILE")
@NoArgsConstructor
@Table(name = "custom_field")
@SuperBuilder
public class CustomFieldFile extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-paperclip";
    }
}
