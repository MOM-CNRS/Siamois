package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.misc.ProjectCreationProgress;

/** Applies one kind of {@link ProjectTemplate} to a freshly created project. */
public interface ProjectTemplateApplier {

    /** Whether this applier handles the given template id. */
    boolean supports(String templateId);

    /**
     * Whether the configuration is written inside the caller's transaction (a failure then cancels the project
     * creation) rather than after it commits.
     */
    boolean transactional();

    /**
     * Configures the new project from the template, planning its steps on the progress and ticking them off.
     *
     * @param info         the user creating the project, in the project's institution
     * @param templateId   the template to apply
     * @param newProjectId the project to configure
     * @param progress     where the configuration reports how far it is
     */
    void apply(UserInfo info, String templateId, Long newProjectId, ProjectCreationProgress progress);
}
