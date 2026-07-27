package fr.siamois.domain.models.form.customfieldanswer;

import jakarta.persistence.*;

@Entity
@Table(name = "custom_field_answer")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class CustomFieldAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "custom_field_answer_id")
    protected Long id;

    public abstract Object getValue();

    public abstract void setValue(Object value);

}
