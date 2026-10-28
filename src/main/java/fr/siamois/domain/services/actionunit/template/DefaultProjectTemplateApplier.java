package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.actionunit.ThesaurusProjectConfigSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The "default project" template: the types of each table, and the vocabularies of their fields, read from the
 * SIAMOIS common-core thesaurus, a list per type where the thesaurus defines one.
 */
@Component
@RequiredArgsConstructor
public class DefaultProjectTemplateApplier implements ProjectTemplateApplier {

    private final ThesaurusProjectConfigSeeder seeder;
    private final ProjectThesaurusAssigner thesaurusAssigner;

    @Override
    public boolean supports(String templateId) {
        return ProjectTemplate.DEFAULT_ID.equals(templateId);
    }

    @Override
    public boolean transactional() {
        return false;
    }

    @Override
    public void apply(UserInfo info, String templateId, Long newProjectId) {
        seeder.seed(info, newProjectId);
        thesaurusAssigner.assignCommonCore(newProjectId);
    }
}
