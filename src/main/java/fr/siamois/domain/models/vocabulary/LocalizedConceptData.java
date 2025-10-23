package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@Entity
@Table(name = "localized_concept_data")
public class LocalizedConceptData {

    @EmbeddedId
    private LocalizedConceptDataId id;

    @MapsId("langCode")
    @Column(name = "lang_code", nullable = false)
    private String langCode;

    @Column(name = "label", nullable = false, columnDefinition = "citext")
    private String label;

    @Column(name = "concept_definition", length = Integer.MAX_VALUE)
    private String definition;

    @MapsId("conceptId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    private Concept concept;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_field_parent_concept_id")
    private Concept parentConcept;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocalizedConceptData that)) return false;
        return Objects.equals(langCode, that.langCode) && Objects.equals(concept, that.concept);
    }

    @Override
    public int hashCode() {
        return Objects.hash(langCode, concept);
    }

    public LocalizedConceptData() {
        this.id = new LocalizedConceptDataId();
    }

    public void setLangCode(String langCode) {
        this.langCode = langCode;
        this.id.langCode =  langCode;
    }

    public void setConcept(Concept concept) {
        this.concept = concept;
        this.id.conceptId = concept.getId();
    }

    @Embeddable
    @AllArgsConstructor
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class LocalizedConceptDataId {
        public Long conceptId;
        public String langCode;

        @Override
        public String toString() {
            return conceptId + "," + langCode;
        }
    }

}
