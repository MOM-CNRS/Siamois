package fr.siamois.domain.models.settings;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

/**
 * Where a project reads a field code, when it is not where the code is read by default (see
 * {@code FieldCodeResolver}): the types of a table, which no field carries, are set this way. The list is
 * either the subtree of the concept carrying {@link #sourceFieldCode} in {@link #vocabulary}, or the subtree
 * of {@link #branchTopTerm}.
 */
@Getter
@Setter
@Entity
@Table(name = "project_code_source",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_code_source_project_code",
                columnNames = {"fk_action_unit_id", "field_code"}))
public class ProjectCodeSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_code_source_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_action_unit_id", nullable = false)
    private ActionUnit actionUnit;

    /** The code being redirected, e.g. the type code of a table. */
    @NotNull
    @Column(name = "field_code", nullable = false)
    private String fieldCode;

    /** The thesaurus {@link #sourceFieldCode} is looked up in. */
    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_vocabulary_id")
    private Vocabulary vocabulary;

    @Nullable
    @Column(name = "source_field_code")
    private String sourceFieldCode;

    @Nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_branch_top_term_id")
    private Concept branchTopTerm;

    public boolean isBranch() {
        return branchTopTerm != null;
    }
}
