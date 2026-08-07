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

    @Column(name = "canonical_key", nullable = false, length = 1000)
    private String canonicalKey;

    /** Next available number. */
    @Column(name = "counter", nullable = false)
    private int counter;
}
