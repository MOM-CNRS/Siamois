package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.actionunit.DefaultProjectIdentifierConfigSeeder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The "empty" template: the default configuration every new project always got. */
@Component
@RequiredArgsConstructor
public class EmptyProjectTemplateApplier implements ProjectTemplateApplier {

    private final DefaultProjectIdentifierConfigSeeder seeder;
    private final ProjectThesaurusAssigner thesaurusAssigner;

    @Override
    public boolean supports(String templateId) {
        return templateId == null || templateId.isBlank() || ProjectTemplate.EMPTY_ID.equals(templateId);
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
