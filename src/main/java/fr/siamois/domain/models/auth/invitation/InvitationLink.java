package fr.siamois.domain.models.auth.invitation;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * A link a super admin shares so that several people can create their own account, until it expires or
 * its accounts quota is reached. Only the hash of the secret token is stored: the link is shown once, when
 * it is created. The accounts created through it point back to it ({@code Person.invitationLink}); how many
 * there are is computed, never stored.
 */
@Entity
@Table(name = "invitation_link")
@Getter
@Setter
public class InvitationLink implements Serializable {

    public static final int DEFAULT_MAX_ACCOUNTS = 1;
    public static final int MAX_ACCOUNTS_LIMIT = 100;

    @Id
    @Column(name = "invitation_link_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "max_accounts", nullable = false)
    private int maxAccounts = DEFAULT_MAX_ACCOUNTS;

    @Column(name = "creation_date", nullable = false)
    private OffsetDateTime creationDate;

    @Column(name = "expiration_date", nullable = false)
    private OffsetDateTime expirationDate;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_created_by", nullable = false)
    private Person createdBy;
}
