package fr.siamois.domain.services;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.auth.Person;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

/**
 * Sets the validation status of any {@link TraceableEntity} (project, recording unit, find, phase,
 * container, place): one place for the write, whatever the entity type. Permission checks are the
 * caller's job — they depend on the entity type (see ValidationStatus#requiresValidatorTo).
 *
 * <p>{@code validatedAt}/{@code validatedBy} record who validated and when; they are cleared when the
 * entity leaves VALIDATED, so they always describe the current validation.</p>
 */
@Service
public class ValidationStatusService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void setStatus(@NonNull Class<? extends TraceableEntity> type, long id,
                          @NonNull ValidationStatus status, Long personId) {
        TraceableEntity entity = entityManager.find(type, id);
        if (entity == null) {
            throw new NoSuchElementException(type.getSimpleName() + " " + id + " not found");
        }
        if (entity.getValidated() == status) {
            return;
        }
        entity.setValidated(status);
        if (status == ValidationStatus.VALIDATED) {
            entity.setValidatedAt(OffsetDateTime.now());
            entity.setValidatedBy(personId != null ? entityManager.getReference(Person.class, personId) : null);
        } else {
            entity.setValidatedAt(null);
            entity.setValidatedBy(null);
        }
    }
}
