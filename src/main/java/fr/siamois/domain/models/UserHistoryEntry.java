package fr.siamois.domain.models;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * A page a user opened in an organisation: the main pane, and the overview next to it when there was one.
 * Visiting the same pair again replaces its entry by a newer one (it is not added twice); only the latest ones are kept.
 * An empty {@code secondaryUri} means no overview (kept as a value, not null, so the pair can be looked up).
 * An entry is identified by who visited, where and when: the time of a visit never changes once recorded.
 */
@Entity
@Table(name = "user_history")
@Getter
@Setter
@NoArgsConstructor
public class UserHistoryEntry {

    @EmbeddedId
    private UserHistoryEntryId id = new UserHistoryEntryId();

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_person", nullable = false)
    private Person person;

    @MapsId("institutionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution", nullable = false)
    private Institution institution;

    @Column(name = "main_uri", nullable = false, length = 2000)
    private String mainUri;
    @Column(name = "main_title", length = 500)
    private String mainTitle;
    @Column(name = "main_icon", length = 100)
    private String mainIcon;
    @Column(name = "main_style_class", length = 100)
    private String mainStyleClass;

    @Column(name = "secondary_uri", nullable = false, length = 2000)
    private String secondaryUri = "";
    @Column(name = "secondary_title", length = 500)
    private String secondaryTitle;
    @Column(name = "secondary_icon", length = 100)
    private String secondaryIcon;
    @Column(name = "secondary_style_class", length = 100)
    private String secondaryStyleClass;

    /**
     * Sets who visited the page, in the entry's key too.
     */
    public void setPerson(Person person) {
        this.person = person;
        this.id.setPersonId(person.getId());
    }

    /**
     * Sets the organisation the page was visited in, in the entry's key too.
     */
    public void setInstitution(Institution institution) {
        this.institution = institution;
        this.id.setInstitutionId(institution.getId());
    }

    /**
     * The time of the visit.
     */
    public Instant getVisitedAt() {
        return id.getVisitedAt();
    }

    /**
     * Sets the time of the visit, before the entry is recorded.
     */
    public void setVisitedAt(Instant visitedAt) {
        this.id.setVisitedAt(visitedAt);
    }

    /**
     * Who visited, in which organisation and when: in this order, the key's index gives a person's history in an
     * organisation directly, the latest visits first when read backwards.
     */
    @Data
    @Embeddable
    public static class UserHistoryEntryId implements Serializable {
        private Long personId;
        private Long institutionId;
        @Column(name = "visited_at", nullable = false)
        private Instant visitedAt;
    }
}
