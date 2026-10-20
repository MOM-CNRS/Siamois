package fr.siamois.domain.models;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A page a user opened in an organisation: the main pane, and the overview next to it when there was one.
 * Visiting the same pair again moves its entry up (it is not added twice); only the latest ones are kept.
 * An empty {@code secondaryUri} means no overview (kept as a value, not null, so the pair can be looked up).
 */
@Entity
@Table(name = "user_history",
        indexes = @Index(name = "idx_user_history_person_institution", columnList = "fk_person, fk_institution, visited_at"))
@Getter
@Setter
@NoArgsConstructor
public class UserHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_person", nullable = false)
    private Person person;

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

    @Column(name = "visited_at", nullable = false)
    private Instant visitedAt;
}
