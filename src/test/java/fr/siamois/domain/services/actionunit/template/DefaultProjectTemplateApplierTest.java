package fr.siamois.domain.services.actionunit.template;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.actionunit.ThesaurusProjectConfigSeeder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultProjectTemplateApplierTest {

    private final ThesaurusProjectConfigSeeder seeder = mock(ThesaurusProjectConfigSeeder.class);
    private final ProjectThesaurusAssigner assigner = mock(ProjectThesaurusAssigner.class);
    private final DefaultProjectTemplateApplier applier = new DefaultProjectTemplateApplier(seeder, assigner);

    @Test
    void supports_onlyTheDefaultTemplate() {
        assertThat(applier.supports(ProjectTemplate.DEFAULT_ID)).isTrue();
        assertThat(applier.supports(ProjectTemplate.EMPTY_ID)).isFalse();
        assertThat(applier.supports(null)).isFalse();
        assertThat(applier.supports("project:5")).isFalse();
    }

    @Test
    void apply_seedsTheProjectAfterTheCreationCommitted() {
        UserInfo info = mock(UserInfo.class);

        applier.apply(info, ProjectTemplate.DEFAULT_ID, 42L);

        assertThat(applier.transactional()).isFalse();
        verify(seeder).seed(info, 42L);
        verify(assigner).assignCommonCore(42L);
    }
}
