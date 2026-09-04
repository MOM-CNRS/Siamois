package fr.siamois.domain.models;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** A single completed run of the "motherlode" Snake easter egg, scored for its player. */
@Data
@Entity
@Table(name = "game_score", schema = "public")
@NoArgsConstructor
public class GameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_score_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Person person;

    /** Institution active when the run was played — scores and rankings never cross institutions. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Institution institution;

    @Column(name = "game", nullable = false)
    private String game;

    @Column(name = "score", nullable = false)
    private int score;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

}
