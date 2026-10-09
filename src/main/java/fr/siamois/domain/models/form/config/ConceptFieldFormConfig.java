package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "field_form_config_concept")
@NoArgsConstructor
@AllArgsConstructor
@Audited
public class ConceptFieldFormConfig extends FieldFormConfig {

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_concept_top_term_id")
    private Concept branchTopTerm;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_collection_id")
    private ConceptCollection collection;

    /**
     * Whether the list follows its source or is a frozen set of concepts. The column default lets
     * Hibernate's schema update add it to a table that already has rows. Not audited: the audit table
     * would need the same default, and nothing reads the mode's history.
     */
    @NotAudited
    @Enumerated(EnumType.STRING)
    @Column(name = "vocabulary_mode", nullable = false, length = 16, columnDefinition = "VARCHAR(16) NOT NULL DEFAULT 'FOLLOW'")
    private VocabularyMode vocabularyMode = VocabularyMode.FOLLOW;

    public ConceptFieldFormConfig(FieldFormConfig fieldFormConfig) {
        super(fieldFormConfig);
    }

    public boolean isFrozen() {
        return vocabularyMode == VocabularyMode.FROZEN;
    }

    public boolean isBranchConfig() {
        return branchTopTerm != null && collection == null;
    }

    public boolean isNotValid() {
        return branchTopTerm == null && collection == null;
    }
}
