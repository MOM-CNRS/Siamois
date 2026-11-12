package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.misc.ProjectCreationProgress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectCreationProgressRegistryTest {

    private static final Long PERSON = 1L;
    private static final Long SOMEONE_ELSE = 2L;

    private final ProjectCreationProgressRegistry registry = new ProjectCreationProgressRegistry();

    @Test
    void percentageOf_readsTheCreationOpenedWithTheToken() {
        ProjectCreationProgress progress = registry.open(PERSON, "token");
        progress.start(4);
        progress.advance();

        assertThat(registry.percentageOf(PERSON, "token")).isEqualTo(25);
    }

    @Test
    void percentageOf_isZero_forAnUnknownToken() {
        assertThat(registry.percentageOf(PERSON, "unknown")).isZero();
    }

    @Test
    void percentageOf_doesNotReadTheCreationOfSomeoneElse() {
        registry.open(PERSON, "token").complete();

        assertThat(registry.percentageOf(SOMEONE_ELSE, "token")).isZero();
    }

    @Test
    void open_withoutAToken_isNotFollowed() {
        registry.open(PERSON, null).complete();
        registry.open(PERSON, " ").complete();

        assertThat(registry.percentageOf(PERSON, null)).isZero();
        assertThat(registry.percentageOf(PERSON, " ")).isZero();
    }

    @Test
    void open_withATooLongToken_isNotFollowed() {
        String token = "t".repeat(ProjectCreationProgressRegistry.MAX_TOKEN_LENGTH + 1);

        registry.open(PERSON, token).complete();

        assertThat(registry.percentageOf(PERSON, token)).isZero();
    }
}
