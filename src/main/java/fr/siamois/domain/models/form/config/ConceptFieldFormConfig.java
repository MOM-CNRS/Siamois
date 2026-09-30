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

    public ConceptFieldFormConfig(FieldFormConfig fieldFormConfig) {
        super(fieldFormConfig);
    }

    public boolean isBranchConfig() {
        return branchTopTerm != null && collection == null;
    }

    public boolean isNotValid() {
        return branchTopTerm == null && collection == null;
    }
}
