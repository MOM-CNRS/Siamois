package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.auth.invitation.InvitationLink;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvitationLinkRepository extends JpaRepository<InvitationLink, Long> {

    Optional<InvitationLink> findByTokenHash(String tokenHash);

    /** Same as {@link #findByTokenHash} but locks the row, so concurrent sign-ups on one link are serialised. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM InvitationLink l WHERE l.tokenHash = :tokenHash")
    Optional<InvitationLink> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<InvitationLink> findAllByOrderByCreationDateDesc();
}
