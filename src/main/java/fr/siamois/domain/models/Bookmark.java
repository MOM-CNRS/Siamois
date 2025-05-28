package fr.siamois.domain.models;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "resource_uri", nullable = false)
    private String resourceUri;


}
