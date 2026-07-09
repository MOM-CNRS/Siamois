package fr.siamois.domain.models.auth.pending;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.Profile;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Data
@Entity
@Table(name = "pending_institution_invitation")
public class PendingInstitutionInvite implements Serializable {

    @Id
    @Column(name = "pending_institution_invitation_id")
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_pending_person_id", nullable = false)
    private PendingPerson pendingPerson;

    /**
     * Profiles to assign to the person once they complete their registration.
     * ORGANISATION-scoped profiles apply to {@link #institution}; PROJECT-scoped
     * profiles carry their own action unit.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "pending_invite_profile",
            joinColumns = @JoinColumn(name = "fk_pending_institution_invitation_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_profile_id"),
            indexes = {
                    @Index(columnList = "fk_pending_institution_invitation_id", name = "idx_pending_invite_profile_invite"),
                    @Index(columnList = "fk_profile_id", name = "idx_pending_invite_profile_profile")
            }
    )
    private transient Set<Profile> profiles = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PendingInstitutionInvite that)) return false;
        return Objects.equals(institution, that.institution) && Objects.equals(pendingPerson, that.pendingPerson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institution, pendingPerson);
    }
}
