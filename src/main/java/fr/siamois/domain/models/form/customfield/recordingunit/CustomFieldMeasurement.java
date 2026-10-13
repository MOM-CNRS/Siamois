package fr.siamois.domain.models.form.customfield.recordingunit;

import fr.siamois.domain.models.FieldCode;
import fr.siamois.domain.models.form.measurement.UnitDefinition;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashSet;
import java.util.Set;


@Getter
@Setter
@Entity
@DiscriminatorValue("MEASUREMENT")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldMeasurement extends CustomFieldOnTheFly {

    @Column(name = "min_value")
    private Long minValue ;

    @Column(name = "max_value")
    private Long maxValue ;

    /**
     * The default unit of the field: the one a new answer is entered in unless the user picks
     * another of {@link #allowedUnits}.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_unit")
    private UnitDefinition unit;

    /**
     * The units a user may enter an answer in, all of the same dimension as {@link #unit}. Empty
     * for a field that only ever uses its default unit, which is how every field created before
     * this list existed reads: see {@link #unitsToChooseFrom()}.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "custom_field_unit",
            joinColumns = @JoinColumn(name = "fk_custom_field_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_unit_id"))
    private Set<UnitDefinition> allowedUnits = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_nature")
    private Concept measurementNature;

    @FieldCode
    public static final String MEASUREMENT_TYPE_FIELD_CODE = "SIAMD.TYPE";

    @FieldCode
    public static final String MEASUREMENT_NATURE_FIELD_CODE = "SIAMD.NATURE";

    /** The units an answer may be entered in: the allowed ones, and always the default unit. */
    public Set<UnitDefinition> unitsToChooseFrom() {
        Set<UnitDefinition> units = new LinkedHashSet<>();
        if (unit != null) units.add(unit);
        if (allowedUnits != null) units.addAll(allowedUnits);
        return units;
    }

    @Override
    public String getIcon() {
        return "bi bi-flask";
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
