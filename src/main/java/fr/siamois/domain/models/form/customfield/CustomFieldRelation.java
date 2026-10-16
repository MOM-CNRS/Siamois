package fr.siamois.domain.models.form.customfield;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * One side of a configurable relation, as a field of an entity's form: the links of a definition seen from one end
 * (see {@link fr.siamois.infrastructure.database.repositories.relation.DefinitionRelation}, which the field's
 * {@code valueBinding} describes). Its values are the entities at the other end; they are never answers of the
 * field but rows of {@code relation_link}, so they are read through the relation machinery, never loaded whole.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("SELECT_MULTIPLE_RELATION")
@Table(name = "custom_field")
@SuperBuilder
@NoArgsConstructor
public class CustomFieldRelation extends CustomField {

    @Override
    public String getIcon() {
        return "bi bi-diagram-2";
    }
}
