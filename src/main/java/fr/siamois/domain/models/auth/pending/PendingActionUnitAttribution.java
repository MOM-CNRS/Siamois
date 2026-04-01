package fr.siamois.domain.models.auth.pending;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Data
@Entity
@Table(name = "pending_action_unit_attribution")
@NoArgsConstructor
public class PendingActionUnitAttribution implements Serializable {

    @EmbeddedId
    private PendingActionUnitId id;

    @MapsId("pendingInstitutionInviteId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_pending_institution_invite_id")
    private PendingInstitutionInvite institutionInvite;

    @MapsId("actionUnitId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_action_unit_id")
    private ActionUnit actionUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_role_id")
    private Concept role = null;

    public PendingActionUnitAttribution(PendingInstitutionInvite invite, ActionUnit actionUnit) {
        this.id = new PendingActionUnitId(invite, actionUnit);
        this.institutionInvite = invite;
        this.actionUnit = actionUnit;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class PendingActionUnitId implements Serializable {
        private Long pendingInstitutionInviteId;
        private Long actionUnitId;

        public PendingActionUnitId(PendingInstitutionInvite invite, ActionUnit actionUnit) {
            this.pendingInstitutionInviteId = invite.getId();
            this.actionUnitId = actionUnit.getId();
        }
    }

}
