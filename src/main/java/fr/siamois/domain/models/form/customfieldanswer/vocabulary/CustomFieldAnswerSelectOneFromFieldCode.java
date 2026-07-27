package fr.siamois.domain.models.form.customfieldanswer.vocabulary;

import fr.siamois.domain.models.form.customfieldanswer.CustomFieldAnswerLegacy;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Objects;


@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@SuperBuilder
@NoArgsConstructor
@DiscriminatorValue("SELECT_ONE_FROM_FIELD_CODE")
public class CustomFieldAnswerSelectOneFromFieldCode extends CustomFieldAnswerSelectOne {

}
