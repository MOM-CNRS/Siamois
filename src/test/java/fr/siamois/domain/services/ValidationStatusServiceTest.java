package fr.siamois.domain.services;

import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.phase.Phase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationStatusServiceTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ValidationStatusService service;

    @Test
    void validating_recordsWhoAndWhen() {
        Phase phase = new Phase();
        Person validator = new Person();
        when(entityManager.find(Phase.class, 3L)).thenReturn(phase);
        when(entityManager.getReference(Person.class, 7L)).thenReturn(validator);

        service.setStatus(Phase.class, 3L, ValidationStatus.VALIDATED, 7L);

        assertThat(phase.getValidated()).isEqualTo(ValidationStatus.VALIDATED);
        assertThat(phase.getValidatedBy()).isSameAs(validator);
        assertThat(phase.getValidatedAt()).isNotNull();
    }

    @Test
    void leavingValidated_clearsWhoAndWhen() {
        Phase phase = new Phase();
        phase.setValidated(ValidationStatus.VALIDATED);
        phase.setValidatedAt(OffsetDateTime.now());
        phase.setValidatedBy(new Person());
        when(entityManager.find(Phase.class, 3L)).thenReturn(phase);

        service.setStatus(Phase.class, 3L, ValidationStatus.CANCELLED, 7L);

        assertThat(phase.getValidated()).isEqualTo(ValidationStatus.CANCELLED);
        assertThat(phase.getValidatedAt()).isNull();
        assertThat(phase.getValidatedBy()).isNull();
    }

    @Test
    void unknownEntity_throws() {
        assertThatThrownBy(() -> service.setStatus(Phase.class, 9L, ValidationStatus.COMPLETE, 7L))
                .isInstanceOf(NoSuchElementException.class);
    }
}
