package fr.siamois.domain.models.form.customfield;

import fr.siamois.domain.models.form.customfield.vocabulary.CustomFieldConcept;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One side of a configurable relation, as a field of a form (docs/modele-relations.md, §3): the links of its
 * {@link fr.siamois.domain.models.relation.RelationDefinition} seen from that side. Its values are the records at
 * the other end; they are never answers of the field but rows of {@code relation_link}. Single or multiple choice
 * is not a property of the field: it is a rule of the relation, per side and per scope.
 * <p>
 * A concept field, so that its vocabulary can be configured like any other: for a complex relation, the field is its
 * own qualifier, as the stratigraphic fields are, and its vocabulary lists the qualifiers it offers.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("RELATION")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldRelation extends CustomFieldConcept {

    @Override
    public String getIcon() {
        return "bi bi-diagram-2";
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
