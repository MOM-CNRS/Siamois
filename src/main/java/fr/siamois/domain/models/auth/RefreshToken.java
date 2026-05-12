package fr.siamois.domain.models.auth;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Jeton de rafraîchissement opaque stocké en base (hash seulement) pour révocation au logout.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "refresh_token", schema = "public")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_person_id", nullable = false)
    private Person person;

    /**
     * Empreinte SHA-256 hex du jeton brut transmis au client (jamais le jeton en clair).
     */
    @NotNull
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
