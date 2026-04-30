package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_RECORDING_UNIT")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectMultipleRecordingUnit extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-pencil-square";
    }

}
