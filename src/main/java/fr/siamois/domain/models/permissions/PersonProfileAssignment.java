package fr.siamois.domain.models.permissions;

import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "person_profile_assignment")
public class PersonProfileAssignment {

    @EmbeddedId
    private PersonProfileAssignmentId id;

    @MapsId("profileId")
    @ManyToOne(fetch = FetchType.LAZY)
    private Profile profile;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY)
    private Person person;

    public PersonProfileAssignment() {
        id = new PersonProfileAssignmentId();
        profile = null;
        person = null;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
        this.id.profileId = profile.getId();
    }

    public void setPerson(Person person) {
        this.person = person;
        this.id.personId = person.getId();
    }

    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class PersonProfileAssignmentId {
        private Long profileId;
        private Long personId;
    }

}
