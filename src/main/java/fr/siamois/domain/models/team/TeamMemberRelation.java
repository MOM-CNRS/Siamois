package fr.siamois.domain.models.team;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.ws.rs.DefaultValue;
import lombok.*;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "team_member")
@Data
@NoArgsConstructor
public class TeamMemberRelation implements Serializable{

    @EmbeddedId
    private TeamMemberId id;

    @MapsId("actionUnitId")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_action_unit_id", nullable = false)
    private ActionUnit actionUnit;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_role_id")
    private Concept role = null;

    @DefaultValue("NOW()")
    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt = OffsetDateTime.now();

    public TeamMemberRelation(ActionUnit actionUnit, Person person) {
        this.id = new TeamMemberId(actionUnit.getId(), person.getId());
        this.actionUnit = actionUnit;
        this.person = person;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TeamMemberRelation that)) return false;
        return Objects.equals(actionUnit, that.actionUnit) && Objects.equals(person, that.person) && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actionUnit, person, role);
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class TeamMemberId implements Serializable {
        private Long actionUnitId;
        private Long personId;

        public TeamMemberId(Long actionUnitId, Long personId) {
            this.actionUnitId = actionUnitId;
            this.personId = personId;
        }

    }

}
