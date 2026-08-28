package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.infrastructure.database.id.AssignedOrSequenceIdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import java.io.Serializable;
import java.util.Objects;


@Getter
@Setter
@Entity
@SuperBuilder(toBuilder = true)
@Table(name = "custom_field")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "answer_type", discriminatorType = DiscriminatorType.STRING)
@NoArgsConstructor
@AllArgsConstructor
public abstract class CustomField implements Serializable {

    /**
     * System fields (see {@code SystemFieldCatalog}) carry a fixed negative id assigned in code;
     * {@link AssignedOrSequenceIdGenerator} persists that id as-is instead of generating one, so
     * every environment refers to the same system field by the same id. Any other field (created
     * through a project's field configuration screen) has no id of its own and gets the next value
     * of {@code custom_field_id_seq} instead.
     */
    @Id
    @GeneratedValue(generator = "custom_field_id_generator")
    @GenericGenerator(
            name = "custom_field_id_generator",
            type = AssignedOrSequenceIdGenerator.class,
            parameters = @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = "custom_field_id_seq")
    )
    @Column(name = "custom_field_id", nullable = false)
    private Long id;

    @Column(name = "label")
    private String label; // label or label code if system field

    // System or custom field
    @Column(name = "is_system_field")
    private Boolean isSystemField;

    @Column(name = "hint")
    private String hint;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_concept")
    private Concept concept;

    @Column(name = "value_binding")
    private String valueBinding; // e.g. "creationTime", "authors" to bind system field with JPA entities attributes

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_author")
    private Person author;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomField that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }


    public String getIcon() {
        return "bi bi-question";
    }

    public String getConceptUri() {
        if (concept == null || concept.getVocabulary() == null) {
            return null;
        }
        String vocabularyUri = concept.getVocabulary().getUri();
        return vocabularyUri+"&idc="+concept.getExternalId();
    }

}
