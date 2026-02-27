package fr.siamois.ui.viewmodel.fieldanswer;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerId;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public abstract class CustomFieldAnswerViewModel implements Serializable {

    private CustomFieldAnswerId pk;
    private Boolean hasBeenModified ;
}
