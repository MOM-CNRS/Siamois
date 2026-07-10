package fr.siamois.domain.models.auth.pending;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A pending person represents a person invited to the application.
 * The pending person has a disabled person associated with it and has profiles associated.
 * If the pending person expires, the profile attributions are deleted as well.
 */
@Entity
@Table(name = "pending_person")
@Data
public class PendingPerson implements Serializable {

    @Id
    @Column(name = "pending_person_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "register_token")
    private String registerToken;

    @Column(name = "register_token_expiration_date")
    private OffsetDateTime pendingInvitationExpirationDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_person_id")
    private Person disabledPerson;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PendingPerson pendingPerson)) return false;

        return Objects.equals(getDisabledPerson(), pendingPerson.getDisabledPerson());
    }

    @Override
    public int hashCode() {
        return disabledPerson.hashCode();
    }

}
