package fr.siamois.domain.models.form.customfield.recordingunit;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_RECORDING_UNIT")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldSelectMultipleRecordingUnit extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-pencil-square";
    }

    @Override
    public String getEditFieldTemplatePath() {
        return "/pages/shared/field/types/recordingUnitMultiple.xhtml";
    }

}
