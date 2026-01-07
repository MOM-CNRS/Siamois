package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@SuperBuilder
@Entity
@DiscriminatorValue("SELECT_ONE_ACTION_CODE")
@Table(name = "custom_field")
public class CustomFieldSelectOneActionCode extends CustomField {

}
