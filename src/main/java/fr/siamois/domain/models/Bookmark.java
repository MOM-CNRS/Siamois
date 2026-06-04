package fr.siamois.domain.models;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Data
@Entity
@Table(name = "bookmark", schema = "public")
@NoArgsConstructor
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bookmark_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Institution institution;

    @Column(name = "title_code", nullable = false)
    private String titleCode;

    @Column(name = "resource_uri", nullable = false, length = 2000)
    private String resourceUri;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Override
    public String toString() {
        return String.format("Bookmark n°%s to %s", id, resourceUri);
    }

}
