package fr.siamois.domain.models.form.customfieldanswer;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "custom_field_answer")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "answer_type", discriminatorType = DiscriminatorType.STRING)
public abstract class CustomFieldAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "custom_field_answer_id")
    protected Long id;

    // Not persisted, used in UI
    private Boolean hasBeenModified;

    public abstract Object getValue();

    public abstract void setValue(Object value);

}
