package fr.siamois.domain.models.recordingunit;

import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.util.Objects;


@Entity
@Table(name = "stratigraphic_relationship")
@Getter
@Setter
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
public class StratigraphicRelationship {

    @EmbeddedId
    private StratigraphicRelationshipId id;

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
    private Concept type;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;  // Same reference
        if (obj == null || getClass() != obj.getClass()) return false;  // Different types

        StratigraphicRelationship that = (StratigraphicRelationship) obj;
        return Objects.equals(unit1, that.unit1) &&
                Objects.equals(unit2, that.unit2) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit1, unit2, type);
    }


}
