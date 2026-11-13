package fr.siamois.domain.models.recordingunit;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;


@Entity
@Table(name = "stratigraphic_relationship")
@Getter
@Setter
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
public class StratigraphicRelationship implements Serializable {

    @EmbeddedId
    private StratigraphicRelationshipId id = new StratigraphicRelationshipId();

    @NotNull
    @MapsId("unit1Id") // Maps to primary key in composite key
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_recording_unit_1_id", nullable = false)
    private RecordingUnit unit1;

    @MapsId("unit2Id")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_recording_unit_2_id", nullable = false)
    private RecordingUnit unit2;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_relationship_concept_id")
    private Concept concept;

    @Column(name = "asynchronous")
    private Boolean isAsynchronous;

    @Column(name = "concept_direction")
    private Boolean conceptDirection;

    @Column(name = "uncertain")
    private Boolean uncertain;

    /**
     * Takes the relationship out of the collections of both its units. Those collections cascade
     * every operation to the relationship: as long as a managed unit still holds it, the flush
     * persists it again and its deletion is silently cancelled.
     */
    public void detachFromUnits() {
        unit1.removeRelationshipAsUnit1(this);
        unit2.removeRelationshipAsUnit2(this);
    }

    /**
     * The same relationship (type, direction, synchronism, uncertainty) with {@code source} replaced by
     * {@code copy}: what a duplicated unit gets from its source's relationships.
     *
     * @param source one of the relationship's units
     * @param copy   the unit taking its place
     * @return the new relationship, not yet persisted
     */
    public StratigraphicRelationship copiedFor(RecordingUnit source, RecordingUnit copy) {
        StratigraphicRelationship copied = new StratigraphicRelationship();
        copied.unit1 = replacing(unit1, source, copy);
        copied.unit2 = replacing(unit2, source, copy);
        copied.id.setUnit1Id(copied.unit1.getId());
        copied.id.setUnit2Id(copied.unit2.getId());
        copied.concept = concept;
        copied.isAsynchronous = isAsynchronous;
        copied.conceptDirection = conceptDirection;
        copied.uncertain = uncertain;
        return copied;
    }

    /**
     * The unit, or {@code copy} when it is {@code source}.
     */
    private static RecordingUnit replacing(RecordingUnit unit, RecordingUnit source, RecordingUnit copy) {
        return Objects.equals(unit.getId(), source.getId()) ? copy : unit;
    }

    /**
     * Whether both units of the relationship are among {@code unitIds}.
     *
     * @param unitIds recording unit ids
     * @return true when the relationship joins two of them
     */
    public boolean joinsUnitsAmong(Set<Long> unitIds) {
        return unitIds.contains(unit1.getId()) && unitIds.contains(unit2.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StratigraphicRelationship other)) return false;
        if (unit1 == null || unit2 == null || other.unit1 == null || other.unit2 == null) return false;

        // unordered equality
        return (unit1.equals(other.unit1) && unit2.equals(other.unit2)) ||
                (unit1.equals(other.unit2) && unit2.equals(other.unit1));
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit1, unit2);
    }

    public record StratRelKey(
            Long unit1Id,
            Long unit2Id
    ) {}

    public static StratRelKey keyOf(StratigraphicRelationship rel) {
        return new StratRelKey(
                rel.getUnit1().getId(),
                rel.getUnit2().getId()
        );
    }


}
