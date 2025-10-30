package fr.siamois.domain.models.specimen;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.Objects;

@Data
@Entity
@Table(name = "specimen_movement")
@Audited
public class Movement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_specimen_id", nullable = false)
    private Specimen specimen;

    @NotNull
    @Column(name = "departure_date", nullable = false)
    private OffsetDateTime departureDate;

    @NotNull
    @Column(name = "return_date", nullable = false)
    private OffsetDateTime returnDate;

    @Column(name = "origin_location", length = Integer.MAX_VALUE)
    private String originLocation;

    @Column(name = "destination_location", length = Integer.MAX_VALUE)
    private String destinationLocation;

    @Column(name = "movement_reason", length = Integer.MAX_VALUE)
    private String movementReason;

    @Column(name = "handled_by", length = Integer.MAX_VALUE)
    private String handledBy;

    @Column(name = "notes", length = Integer.MAX_VALUE)
    private String notes;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Movement movement = (Movement) o;
        return Objects.equals(specimen, movement.specimen)
                && Objects.equals(originLocation, movement.originLocation)
                && Objects.equals(destinationLocation, movement.destinationLocation)
                && Objects.equals(handledBy, movement.handledBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specimen, originLocation, destinationLocation, handledBy);
    }
}
