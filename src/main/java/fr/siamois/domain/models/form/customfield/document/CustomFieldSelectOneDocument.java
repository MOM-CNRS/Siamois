package fr.siamois.domain.models.form.customfield.document;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** One document of the entity's project (for example the georeferencing plan of a project). */
@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_ONE_DOCUMENT")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectOneDocument extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-file-earmark";
    }

}
