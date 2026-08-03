package fr.siamois.domain.models.form.config;

import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.ConceptCollection;
import jakarta.persistence.*;
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
public class ConceptFieldFormConfig extends FieldFormConfig {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_concept_top_term_id")
    private Concept branchTopTerm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_collection_id")
    private ConceptCollection collection;

    public ConceptFieldFormConfig(FieldFormConfig fieldFormConfig) {
        super(fieldFormConfig);
    }
}
