package fr.siamois.domain.models.ark;

import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.io.Serializable;
import java.util.Objects;

@Data
@Entity
@Table(name = "ark", schema = "public")
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
public class Ark implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "internal_id", nullable = false)
    private Long internalId;

    @ManyToOne
    @JoinColumn(name = "fk_institution_id", nullable = false)
    private Institution creatingInstitution;

    @Column(name = "qualifier", nullable = false)
    private String qualifier;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Ark ark)) return false;

        return qualifier.equals(ark.qualifier) && creatingInstitution.equals(ark.creatingInstitution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(creatingInstitution, qualifier);
    }
}