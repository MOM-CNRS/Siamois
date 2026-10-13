package fr.siamois.domain.models.auth.token;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * A single-use token, sent by e-mail, authorising a sensitive account operation (password reset,
 * e-mail change) for a person.
 */
@Entity
@Table(name = "person_action_token")
@Getter
@Setter
public class PersonActionToken implements Serializable {

    @Id
    @Column(name = "person_action_token_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private PersonActionType type;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    /** The address to switch to, only set for {@link PersonActionType#EMAIL_CHANGE}. */
    @Column(name = "new_email")
    private String newEmail;

    @Column(name = "creation_date", nullable = false)
    private OffsetDateTime creationDate;

    @Column(name = "expiration_date", nullable = false)
    private OffsetDateTime expirationDate;
}
