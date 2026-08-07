package fr.siamois.domain.models.identifier;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.form.config.FormConfig;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "identifier_counter", uniqueConstraints = @UniqueConstraint(
        name = "uq_identifier_counter_scope",
        columnNames = {"fk_action_unit_id", "fk_form_config_id", "canonical_key"}))
public class IdentifierCounter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "identifier_counter_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_action_unit_id", nullable = false)
    private ActionUnit actionUnit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_form_config_id", nullable = false)
    private FormConfig formConfig;

    /**
     * Stable, opaque representation of the active counter-partition dimensions.
     *
     * <p>The key is built by {@code IdentifierPartitionService} from only the partition-aware tokens
     * used by the effective identifier format. Dimension names are sorted, then names and non-null values
     * are URL-safe Base64 encoded. The serialized value starts with a version prefix ({@code v1}); a missing
     * relationship or value is represented by the reserved {@code ~} sentinel. When the format activates no
     * partition dimension, the key is simply {@code v1}.</p>
     *
     * <p>This value is intentionally independent from the rendered identifier. For example, a missing numerical
     * token may render as zeroes and a missing textual token as {@code XXX}, while both still address the same
     * missing-value counter bucket. Likewise, entities with the same partition value, such as spatial units sharing
     * one place number, share one counter sequence.</p>
     *
     * <p>Together with {@link #actionUnit} and {@link #formConfig}, this field identifies one unique counter
     * namespace through {@code uq_identifier_counter_scope}. It must always be produced by the partition service
     * and must not be assembled, parsed, or edited manually.</p>
     */
    @Column(name = "canonical_key", nullable = false, length = 1000)
    private String canonicalKey;

    /** Next available number. */
    @Column(name = "counter", nullable = false)
    private int counter;
}
