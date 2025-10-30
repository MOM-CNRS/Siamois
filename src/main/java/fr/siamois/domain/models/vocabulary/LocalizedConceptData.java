package fr.siamois.domain.models.vocabulary;

import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.domain.models.vocabulary.label.LabelType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Objects;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@Table(name = "localized_concept_data")
public class LocalizedConceptData extends ConceptLabel {

    @Column(name = "concept_definition", length = Integer.MAX_VALUE)
    private String definition;

    public LocalizedConceptData() {
        super();
    }

    public void setLangCode(String langCode) {
        this.id.setLangCode(langCode);
    }

    public String getLangCode() {
        return this.id.getLangCode();
    }

    public void setConcept(Concept concept) {
        this.concept = concept;
        if (concept != null) {
            this.id.setConceptId(concept.getId());
        } else {
            this.id.setConceptId(null);
        }
    }

    @Override
    public LabelType getLabelType() {
        return LabelType.PREF_LABEL;
    }
}
