package fr.siamois.domain.models.vocabulary;

import fr.siamois.domain.models.form.customfield.CustomField;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An additional field whose concept is not found yet in a thesaurus: its candidate awaits validation there. The
 * thesaurus is searched again each time it is loaded; once the concept is found, it becomes the field's
 * {@link CustomField#getConcept() concept} and this row is deleted.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "custom_field_pending_candidate", uniqueConstraints = @UniqueConstraint(
        name = "uk_custom_field_pending_candidate",
        columnNames = {"fk_custom_field_id", "fk_vocabulary_id"}),
        indexes = @Index(name = "idx_custom_field_pending_candidate_vocabulary", columnList = "fk_vocabulary_id"))
public class CustomFieldPendingCandidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "custom_field_pending_candidate_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_custom_field_id", nullable = false)
    private CustomField field;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_vocabulary_id", nullable = false)
    private Vocabulary vocabulary;

    /** The language the field's name is written in, which the thesaurus is searched in. */
    @Column(name = "lang", nullable = false, length = 8)
    private String lang;
}
