package fr.siamois.domain.models.identifier;

import fr.siamois.domain.models.form.config.FormConfig;
import fr.siamois.domain.models.institution.Institution;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Counter of an identifier numbered at the institution level (the projects), the counterpart of
 * {@link IdentifierCounter} which is scoped to a project. Allocated by the {@code institution_identifier_nextval}
 * PL/pgSQL function; {@code canonicalKey} follows the same rules as {@link IdentifierCounter#getCanonicalKey()}.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "institution_identifier_counter", uniqueConstraints = @UniqueConstraint(
        name = "uq_institution_identifier_counter_scope",
        columnNames = {"fk_institution_id", "fk_form_config_id", "canonical_key"}))
public class InstitutionIdentifierCounter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "institution_identifier_counter_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_form_config_id", nullable = false)
    private FormConfig formConfig;

    @Column(name = "canonical_key", nullable = false, length = 1000)
    private String canonicalKey;

    /** Next available number. */
    @Column(name = "counter", nullable = false)
    private int counter;
}
