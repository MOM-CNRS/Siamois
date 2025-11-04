package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing localized data for a concept, including its definition.
 * May be extended in the future to include additional fields such as notes, examples, etc.
 *
 * @author Julien Linget
 */
@Data
@Entity
@Table(name = "localized_concept_data")
public class LocalizedConceptData {

    @EmbeddedId
    private Id id;

    @MapsId("conceptId")
    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "fk_concept_id")
    private Concept concept;

    @Column(name = "concept_definition", length = Integer.MAX_VALUE)
    private String definition;

    public LocalizedConceptData() {
        this.id = new Id();
    }

    public String getLangCode() {
        return id.getLangCode();
    }

    public void setLangCode(String langCode) {
        id.setLangCode(langCode);
    }

    @Data
    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Id {
        private Long conceptId;
        private String langCode;
    }

}
