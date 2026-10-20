package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.UserInfo;

/** Applies one kind of {@link ProjectTemplate} to a freshly created project. */
public interface ProjectTemplateApplier {

    /** Whether this applier handles the given template id. */
    boolean supports(String templateId);

    /**
     * Whether the configuration is written inside the caller's transaction (a failure then cancels the project
     * creation) rather than after it commits.
     */
    boolean transactional();

    /** Configures the new project from the template. */
    void apply(UserInfo info, String templateId, Long newProjectId);
}
