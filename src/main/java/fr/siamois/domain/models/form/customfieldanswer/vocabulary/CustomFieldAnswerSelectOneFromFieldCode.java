package fr.siamois.domain.models.form.customfieldanswer.vocabulary;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@NoArgsConstructor
@DiscriminatorValue("SELECT_ONE_FROM_FIELD_CODE")
public class CustomFieldAnswerSelectOneFromFieldCode extends CustomFieldAnswerSelectOne {

}
