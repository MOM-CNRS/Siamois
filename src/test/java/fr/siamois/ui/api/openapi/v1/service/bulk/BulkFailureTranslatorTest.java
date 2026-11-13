package fr.siamois.ui.api.openapi.v1.service.bulk;

import fr.siamois.domain.models.exceptions.HierarchyCycleException;
import fr.siamois.domain.models.exceptions.permission.ForbiddenOperationException;
import fr.siamois.ui.api.openapi.v1.response.bulk.BulkFailureResource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class BulkFailureTranslatorTest {

    private final BulkFailureTranslator translator = new BulkFailureTranslator();

    /**
     * A ResponseStatusException keeps its status and reason.
     */
    @Test
    void keepsTheStatusAndReasonOfAResponseStatusException() {
        BulkFailureResource failure = translator.translate(4L,
                new ResponseStatusException(HttpStatus.CONFLICT, "L'UE a des relations"));

        assertThat(failure).isEqualTo(new BulkFailureResource(4L, 409, "L'UE a des relations"));
    }

    /**
     * A missing right is a 403, whatever the exception says.
     */
    @Test
    void reportsAMissingRightAsForbidden() {
        assertThat(translator.translate(4L, new AccessDeniedException("x")).status()).isEqualTo(403);
        assertThat(translator.translate(5L, new ForbiddenOperationException("x")).status()).isEqualTo(403);
    }

    /**
     * A hierarchy loop is a conflict, with its own message.
     */
    @Test
    void reportsAHierarchyLoopAsAConflict() {
        BulkFailureResource failure = translator.translate(4L, new HierarchyCycleException("Boucle"));

        assertThat(failure).isEqualTo(new BulkFailureResource(4L, 409, "Boucle"));
    }

    /**
     * Anything unexpected is an internal error, without leaking its message.
     */
    @Test
    void reportsAnythingElseAsAnInternalError() {
        BulkFailureResource failure = translator.translate(4L, new IllegalStateException("détail interne"));

        assertThat(failure.status()).isEqualTo(500);
        assertThat(failure.message()).doesNotContain("détail interne");
    }
}
