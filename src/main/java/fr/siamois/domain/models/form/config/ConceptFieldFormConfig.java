package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import fr.siamois.domain.models.vocabulary.Vocabulary;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;

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
     * The thesaurus a {@link #fieldCode} is looked up in. A branch or a collection already belongs to
     * one thesaurus, so this is only set together with a field code.
     */
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_vocabulary_id")
    private Vocabulary vocabulary;

    /** Third way to name the source, next to a branch and a collection: the code that tags the list in {@link #vocabulary}. */
    @Column(name = "field_code")
    private String fieldCode;

    /**
     * The label of the {@link #branchTopTerm} when it was picked. A branch or a collection belongs to one thesaurus and
     * its ids mean nothing in another: this name is what finds the same branch in the thesaurus the field is moved to.
     * Not audited, it only repeats what the thesaurus says.
     */
    @NotAudited
    @Column(name = "branch_name")
    private String branchName;

    /** The label of the {@link #collection} when it was picked, for the same reason as {@link #branchName}. */
    @NotAudited
    @Column(name = "collection_name")
    private String collectionName;

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

    public boolean isCollectionConfig() {
        return collection != null;
    }

    public boolean isFieldCodeConfig() {
        return fieldCode != null && vocabulary != null && branchTopTerm == null && collection == null;
    }

    public boolean isNotValid() {
        return !isBranchConfig() && !isCollectionConfig() && !isFieldCodeConfig();
    }
}
