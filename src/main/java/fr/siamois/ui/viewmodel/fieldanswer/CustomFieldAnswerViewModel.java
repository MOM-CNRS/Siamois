package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
public abstract class CustomFieldAnswerViewModel implements Serializable {

    private CustomFieldAnswerId pk;
    private Boolean hasBeenModified ;
}
