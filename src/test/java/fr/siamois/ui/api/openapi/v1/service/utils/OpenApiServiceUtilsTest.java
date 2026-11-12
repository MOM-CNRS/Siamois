package fr.siamois.ui.api.openapi.v1.service.utils;

import fr.siamois.domain.models.ValidationStatus;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.ui.api.openapi.v1.exception.IdentifierConflictException;
import fr.siamois.ui.api.openapi.v1.request.container.ContainerPatchRequest;
import fr.siamois.ui.api.openapi.v1.resource.form.AnswerInput;
import fr.siamois.ui.api.openapi.v1.service.ValidationOpenApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OpenApiServiceUtilsTest {

    private static final FormPatchRights EDITOR = new FormPatchRights(true, false);
    private static final FormPatchRights VALIDATOR_ONLY = new FormPatchRights(false, true);

    @Mock
    private ValidationOpenApiService validation;

    private ContainerDTO container;
    private ContainerPatchRequest request;

    /**
     * A container « C-1 », in progress, and an empty PATCH.
     */
    @BeforeEach
    void setUp() {
        container = new ContainerDTO();
        container.setIdentifier("C-1");
        container.setValidationStatus(ValidationStatus.INCOMPLETE);
        request = new ContainerPatchRequest();
    }

    /**
     * Answers written by an editor: allowed, the form is to be saved.
     */
    @Test
    void answersByAnEditorAreToBeSaved() {
        request.setAnswers(Map.of("12", new AnswerInput("Remblai", null)));

        FormPatchChanges changes = OpenApiServiceUtils.applyFormPatch(container, request, EDITOR, validation, () -> false);

        assertThat(changes).isEqualTo(new FormPatchChanges(true, false, false));
        assertThat(changes.writesContent()).isTrue();
        verify(validation).requireAllowed(ValidationStatus.INCOMPLETE, null, true, false);
    }

    /**
     * Content written without the edit right: 403, nothing checked further.
     */
    @Test
    void contentWithoutTheEditRightIsForbidden() {
        request.setAnswers(Map.of("12", new AnswerInput("Remblai", null)));

        assertThatThrownBy(() -> OpenApiServiceUtils.applyFormPatch(container, request, VALIDATOR_ONLY, validation, () -> false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(validation);
    }

    /**
     * A validator may change the status alone: nothing else is to be saved.
     */
    @Test
    void aValidatorMayChangeTheStatusAlone() {
        request.setValidationStatus(ValidationStatus.VALIDATED);

        FormPatchChanges changes = OpenApiServiceUtils.applyFormPatch(container, request, VALIDATOR_ONLY, validation, () -> false);

        assertThat(changes.writesContent()).isFalse();
        verify(validation).requireAllowed(ValidationStatus.INCOMPLETE, ValidationStatus.VALIDATED, false, true);
    }

    /**
     * A new identifier is written on the entity, trimmed.
     */
    @Test
    void aNewIdentifierIsWrittenOnTheEntity() {
        request.setIdentifier("  C-2 ");

        FormPatchChanges changes = OpenApiServiceUtils.applyFormPatch(container, request, EDITOR, validation, () -> false);

        assertThat(changes.identifierChange()).isTrue();
        assertThat(container.getIdentifier()).isEqualTo("C-2");
    }

    /**
     * An identifier already used in the project: 409.
     */
    @Test
    void anIdentifierAlreadyUsedIsAConflict() {
        request.setIdentifier("C-2");

        assertThatThrownBy(() -> OpenApiServiceUtils.applyFormPatch(container, request, EDITOR, validation, () -> true))
                .isInstanceOf(IdentifierConflictException.class);
    }
}
