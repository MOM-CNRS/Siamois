package fr.siamois.infrastructure.database.id;

import fr.siamois.domain.models.form.customfield.CustomField;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

/**
 * Uses the entity's own id when it already has one, otherwise falls back to the backing sequence.
 * <p>
 * {@link CustomField} system fields are given a fixed negative id in code (see
 * {@code SystemFieldCatalog}) so {@code field_form_config} and every other foreign key to them
 * stays meaningful across environments, independently of insertion order. Project-created fields
 * have no id of their own and still get a fresh one from the sequence — the sequence only ever
 * produces positive values, so the two ranges never collide.
 */
public class AssignedOrSequenceIdGenerator extends SequenceStyleGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        if (object instanceof CustomField field && field.getId() != null) {
            return field.getId();
        }
        return super.generate(session, object);
    }
}
