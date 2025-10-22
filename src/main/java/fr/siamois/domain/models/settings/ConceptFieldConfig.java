package fr.siamois.domain.models.settings;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.vocabulary.Concept;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
@Table(name = "concept_field_config")
public class ConceptFieldConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_institution_id", nullable = false)
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user_id")
    private Person user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fk_concept_id", nullable = false)
    private Concept concept;

    @NotNull
    @Column(name = "field_code", nullable = false, length = Integer.MAX_VALUE)
    private String fieldCode;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConceptFieldConfig that)) return false;
        return Objects.equals(institution, that.institution)
                && Objects.equals(user, that.user)
                && Objects.equals(concept, that.concept)
                && Objects.equals(fieldCode, that.fieldCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(institution, user, concept, fieldCode);
    }
}