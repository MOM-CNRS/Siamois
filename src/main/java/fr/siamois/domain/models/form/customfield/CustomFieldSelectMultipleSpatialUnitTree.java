package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_SPATIAL_UNIT_TREE")
@Table(name = "custom_field")
@SuperBuilder
public class CustomFieldSelectMultipleSpatialUnitTree extends CustomField {


}
