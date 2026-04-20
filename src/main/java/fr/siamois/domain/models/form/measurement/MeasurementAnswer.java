package fr.siamois.domain.models.form.measurement;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "measurement_answer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeasurementAnswer implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Brut value as entered by the user
     */
    @Column(name = "numeric_value", nullable = false)
    private Double numericValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_unit_id", nullable = false)
    private UnitDefinition unit;

    /**
     * Normalized value (ex Km to meters)
     */
    @Column(name = "normalized_value")
    private Double normalizedValue;

    /**
     * Comment for the measurement
     */
    @Column(columnDefinition = "TEXT")
    private String comment;

    /**
     * PrePersist hook to normalize the answer value
     */
    @PrePersist
    @PreUpdate
    public void calculateNormalization() {
        if (numericValue != null && unit != null) {
            this.normalizedValue = unit.normalize(numericValue);
        }
    }
}
