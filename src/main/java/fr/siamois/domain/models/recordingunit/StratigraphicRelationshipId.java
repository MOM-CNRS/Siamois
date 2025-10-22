package fr.siamois.domain.models.recordingunit;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@Audited
public class StratigraphicRelationshipId implements Serializable {

    private Long unit1Id;
    private Long unit2Id;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StratigraphicRelationshipId that = (StratigraphicRelationshipId) o;
        return Objects.equals(unit1Id, that.unit1Id) &&
                Objects.equals(unit2Id, that.unit2Id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit1Id, unit2Id);
    }
}

