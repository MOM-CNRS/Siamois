package fr.siamois.domain.models.vocabulary;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.io.Serializable;
import java.util.Objects;

@Data
@Entity
@Table(name = "vocabulary_type")
@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
public class VocabularyType implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vocabulary_type_id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "label", nullable = false, length = Integer.MAX_VALUE, unique = true)
    private String label;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VocabularyType vocType)) return false;

        return Objects.equals(label, vocType.label) ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(label);
    }

}