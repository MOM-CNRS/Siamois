package fr.siamois.domain.models.form.customformresponse;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswer;
import fr.siamois.domain.models.form.customform.CustomForm;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Entity
@Data
@Table(name = "custom_form_response")
@ToString
public class CustomFormResponse implements Serializable {


    @Id
    @Column(name="custom_form_response_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name="fk_custom_form_id")
    private CustomForm form;

    @OneToMany(
            fetch = FetchType.EAGER,
            orphanRemoval = true,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            mappedBy = "pk.formResponse"
    )
    @MapKey(name="pk.field")
    private Map<CustomField, CustomFieldAnswer> answers = new HashMap<>();

    @OneToOne(mappedBy = "formResponse")
    @ToString.Exclude  // Prevent infinite loop
    private RecordingUnit recordingUnit;

    // Keep entities in sync
    public void addAnswer(CustomFieldAnswer answer) {
        this.answers.put(answer.getPk().getField(), answer);
        answer.getPk().setFormResponse(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomFormResponse that = (CustomFormResponse) o;
        return Objects.equals(recordingUnit, that.recordingUnit);  // Compare based on RecordingUnit
    }

    @Override
    public int hashCode() {
        return Objects.hash(recordingUnit);  // Hash based on RecordingUnit
    }

}
