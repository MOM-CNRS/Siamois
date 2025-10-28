package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Embeddable
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Data
public class LocalizedConceptDataId {
    private Long conceptId;
    @Column(name = "lang_code", nullable = false)
    private String langCode;

    @Override
    public String toString() {
        return conceptId + "," + langCode;
    }
}
