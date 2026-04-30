package fr.siamois.domain.models.form.measurement;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "unit_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitDefinition implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    private Concept concept;

    @Column(nullable = false)
    private String label;

    @Column(length = 10)
    private String symbol;

    /**
     * The Physical Dimension (LENGTH, VOLUME, MASS, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Dimension dimension;

    /**
     * The multiplier to convert the raw value to the system's base unit.
     * Example for Gallon (US) to Liter: 3.78541
     */
    @Column(name = "factor_to_base", nullable = false)
    private Double factorToBase;

    /**
     * True if this is the pivot unit for the dimension (e.g., Meter for Length).
     */
    @Column(name = "is_system_base", nullable = false)
    private boolean systemBase = false;

    public enum Dimension {
        LENGTH,
        VOLUME,
        MASS,
        TIME,
        ANGLE,
        COUNT
    }

    /**
     * Utility method to normalize a raw value
     */
    public Double normalize(Double rawValue) {
        if (rawValue == null || factorToBase == null) return null;
        return rawValue * factorToBase;
    }
}
