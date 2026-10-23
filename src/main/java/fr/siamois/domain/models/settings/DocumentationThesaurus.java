package fr.siamois.domain.models.settings;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * The thesaurus in which an institution, or one of its projects, documents its own fields and types:
 * the default source of a field the SIAMOIS thesaurus does not define. A project's thesaurus
 * ({@link #actionUnit} set) takes precedence over its institution's.
 */
@Getter
@Setter
@Entity
@Table(name = "documentation_thesaurus")
public class DocumentationThesaurus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "documentation_thesaurus_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution_id", nullable = false)
    private Institution institution;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_action_unit_id")
    private ActionUnit actionUnit;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_vocabulary_id", nullable = false)
    private Vocabulary vocabulary;
}
