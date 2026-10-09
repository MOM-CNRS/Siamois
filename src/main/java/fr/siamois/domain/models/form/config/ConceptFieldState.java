package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.form.customfield.CustomField;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The state of one concept in the list of a vocabulary field, for one form configuration (project × type).
 * A concept without a row is {@link ConceptState#ENABLED} in {@link VocabularyMode#FOLLOW} mode
 * and not offered in {@link VocabularyMode#FROZEN} mode.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "concept_field_state", uniqueConstraints = @UniqueConstraint(
        name = "uk_concept_field_state",
        columnNames = {"fk_custom_field_id", "fk_form_config_id", "fk_concept_id"}),
        indexes = @Index(name = "idx_concept_field_state_field_config",
                columnList = "fk_custom_field_id, fk_form_config_id"))
public class ConceptFieldState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "concept_field_state_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_custom_field_id", nullable = false)
    private CustomField field;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_form_config_id", nullable = false)
    private FormConfig formConfig;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    private Concept concept;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private ConceptState state;
}
