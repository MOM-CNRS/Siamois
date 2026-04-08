package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_SPATIAL_UNIT_TREE")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomFieldSelectMultipleSpatialUnitTree extends CustomField {

    private String source;

}
