package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.form.measurement.UnitDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

public class CustomFieldMeasurement extends CustomField {

    @Column(name = "min_value")
    private Long minValue ;

    @Column(name = "max_value")
    private Long maxValue ;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_unit")
    private UnitDefinition unit;

}
