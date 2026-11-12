package fr.siamois.domain.models.auth.pending;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A pending person represents a person whose account is not active yet. The pending person has a disabled
 * person associated with it and has profiles associated. It is either:
 * <ul>
 *     <li>an invitation: the person was invited by e-mail and registers through the token's link;</li>
 *     <li>awaiting the creation of their account: the person was created on the fly, from their first and
 *     last name only (as the scientific manager of a project, say). They have no e-mail and no token until
 *     a manager links them to an account they create (see {@code PendingAccountPersonService}).</li>
 * </ul>
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

    /**
     * Builds the pending person of someone created without account: no registration token until they are invited.
     *
     * @param disabledPerson the disabled person, created from their name only
     * @return the pending person, not saved yet
     */
    public static PendingPerson awaitingAccountOf(Person disabledPerson) {
        PendingPerson pendingPerson = new PendingPerson();
        pendingPerson.setDisabledPerson(disabledPerson);
        return pendingPerson;
    }

    /**
     * Tells whether this pending person still awaits the creation of their account, i.e. has never been invited.
     *
     * @return {@code true} when no registration token was issued yet
     */
    public boolean isAwaitingAccount() {
        return registerToken == null;
    }

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
