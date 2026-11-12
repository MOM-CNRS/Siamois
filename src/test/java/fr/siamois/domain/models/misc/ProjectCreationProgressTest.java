package fr.siamois.domain.models.misc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectCreationProgressTest {

    @Test
    void percentage_isZero_untilStepsArePlanned() {
        ProjectCreationProgress progress = ProjectCreationProgress.unwatched();

        progress.advance();

        assertThat(progress.getPercentage()).isZero();
    }

    @Test
    void percentage_followsTheStepsDone() {
        ProjectCreationProgress progress = new ProjectCreationProgress();
        progress.start(4);

        progress.advance();
        assertThat(progress.getPercentage()).isEqualTo(25);

        progress.advance(2);
        assertThat(progress.getPercentage()).isEqualTo(75);
    }

    @Test
    void percentage_neverExceedsOneHundred() {
        ProjectCreationProgress progress = new ProjectCreationProgress();
        progress.start(2);

        progress.advance(5);

        assertThat(progress.getPercentage()).isEqualTo(100);
    }

    @Test
    void start_restartsTheCount() {
        ProjectCreationProgress progress = new ProjectCreationProgress();
        progress.start(2);
        progress.advance(2);

        progress.start(4);

        assertThat(progress.getPercentage()).isZero();
    }

    @Test
    void complete_readsOneHundred_evenWhenNothingWasPlanned() {
        ProjectCreationProgress planned = new ProjectCreationProgress();
        planned.start(8);
        planned.advance();
        ProjectCreationProgress unplanned = new ProjectCreationProgress();

        planned.complete();
        unplanned.complete();

        assertThat(planned.getPercentage()).isEqualTo(100);
        assertThat(unplanned.getPercentage()).isEqualTo(100);
    }

    @Test
    void reset_forgetsTheCreation() {
        ProjectCreationProgress progress = new ProjectCreationProgress();
        progress.start(2);
        progress.complete();

        progress.reset();

        assertThat(progress.getPercentage()).isZero();
    }
}
