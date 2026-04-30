package fr.siamois.dto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptDTO implements Serializable {

    private String externalId;
    private long id;
    private VocabularyDTO vocabulary;
    private boolean deleted;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConceptDTO concept)) return false;

        return Objects.equals(externalId, concept.externalId) &&
                Objects.equals(vocabulary, concept.vocabulary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(externalId, vocabulary);
    }

}
