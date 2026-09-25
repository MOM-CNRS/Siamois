package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.domain.models.phase.Phase;
import fr.siamois.domain.services.ValidationStatusService;
import fr.siamois.dto.entity.PersonDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ValidationOpenApiServiceTest {

    @Mock
    private ValidationStatusService validationStatusService;

    @InjectMocks
    private ValidationOpenApiService service;

    private static void assertForbidden(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void editor_movesBetweenEnCoursTermineAnnule() {
        assertThatCode(() -> service.requireAllowed(ValidationStatus.INCOMPLETE, ValidationStatus.CANCELLED, true, false))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.requireAllowed(ValidationStatus.CANCELLED, ValidationStatus.COMPLETE, true, false))
                .doesNotThrowAnyException();
    }

    @Test
    void editorWithoutValidatorRight_cannotValidate_norUnvalidate() {
        assertForbidden(() -> service.requireAllowed(ValidationStatus.COMPLETE, ValidationStatus.VALIDATED, true, false));
        assertForbidden(() -> service.requireAllowed(ValidationStatus.VALIDATED, ValidationStatus.INCOMPLETE, true, false));
    }

    @Test
    void validator_validates_evenWithoutTheEditRight() {
        assertThatCode(() -> service.requireAllowed(ValidationStatus.COMPLETE, ValidationStatus.VALIDATED, false, true))
                .doesNotThrowAnyException();
    }

    @Test
    void readOnlyCaller_cannotChangeTheStatus_butAnAbsentOrUnchangedStatusIsNoChange() {
        assertForbidden(() -> service.requireAllowed(ValidationStatus.INCOMPLETE, ValidationStatus.COMPLETE, false, false));
        assertThatCode(() -> service.requireAllowed(ValidationStatus.INCOMPLETE, null, false, false)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireAllowed(ValidationStatus.VALIDATED, ValidationStatus.VALIDATED, false, false))
                .doesNotThrowAnyException();
        assertThat(ValidationOpenApiService.changes(ValidationStatus.COMPLETE, null)).isFalse();
    }

    @Test
    void apply_writesThroughTheDomainService_andIgnoresAnAbsentStatus() {
        PersonDTO person = new PersonDTO();
        person.setId(7L);

        service.apply(Phase.class, 3L, null, person);
        verifyNoInteractions(validationStatusService);

        service.apply(Phase.class, 3L, ValidationStatus.VALIDATED, person);
        verify(validationStatusService).setStatus(Phase.class, 3L, ValidationStatus.VALIDATED, 7L);
    }
}
