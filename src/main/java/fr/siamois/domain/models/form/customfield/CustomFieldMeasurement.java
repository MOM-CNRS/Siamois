package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;


@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Entity
@DiscriminatorValue("MEASUREMENT")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldMeasurement extends CustomField {

    @Column(name = "min_value")
    private Long minValue ;

    @Column(name = "max_value")
    private Long maxValue ;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_unit")
    private UnitDefinition unit;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_nature")
    private Concept measurementNature;

}
