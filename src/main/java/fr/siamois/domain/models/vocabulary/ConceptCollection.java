package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "concept_collection")
@NoArgsConstructor
public class ConceptCollection {

    @NonNull
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collection_id")
    private Long id;

    @NonNull
    @Column(name = "external_id")
    private String externalId;

    @NonNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_vocabulary_id", nullable = false)
    private Vocabulary vocabulary;

    @Nullable
    @Column(name = "existing_hash")
    private String existingHash;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "collection_concept",
            joinColumns = @JoinColumn(name = "fk_collection_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_concept_id")
    )
    private Set<Concept> concepts = new HashSet<>();
}
