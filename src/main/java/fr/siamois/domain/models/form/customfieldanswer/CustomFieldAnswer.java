package fr.siamois.domain.models.form.customfieldanswer;

import fr.siamois.domain.models.form.config.FormConfigAnswer;
import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@Entity
@Table(name = "custom_field_answer")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "answer_type", discriminatorType = DiscriminatorType.STRING)
@NoArgsConstructor
@AllArgsConstructor
public abstract class CustomFieldAnswer {

    @EmbeddedId
    protected CustomFieldAnswerId id = new CustomFieldAnswerId();

    @NonNull
    @MapsId("customFieldId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_custom_field_id", nullable = false)
    protected CustomField customField;

    @NonNull
    @MapsId("formConfigAnswerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_form_config_answer_id", nullable = false)
    protected FormConfigAnswer formConfigAnswer;

    // Not persisted, used in UI
    private Boolean hasBeenModified;

    public abstract Object getValue();

    public abstract void setValue(Object value);

    public void setCustomField(@NonNull CustomField customField) {
        this.customField = customField;
        this.id.customFieldId = customField.getId();
    }

    public void setFormConfigAnswer(@NonNull FormConfigAnswer formConfigAnswer) {
        this.formConfigAnswer = formConfigAnswer;
        this.id.formConfigAnswerId = formConfigAnswer.getId();
    }

    @Embeddable
    @Data
    public static class CustomFieldAnswerId {
        private Long formConfigAnswerId;
        private Long customFieldId;
    }

}
