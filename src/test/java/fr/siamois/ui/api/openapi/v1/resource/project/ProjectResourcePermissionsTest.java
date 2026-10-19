package fr.siamois.ui.api.openapi.v1.resource.project;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectResourcePermissionsTest {

    @Test
    void editRightNeverImpliesTheDeleteRight() {
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(true);

        assertThat(permissions.canEdit()).isTrue();
        assertThat(permissions.canDelete()).isFalse();
    }

    @Test
    void withDelete_setsOnlyTheDeleteRight() {
        ProjectResourcePermissions permissions = ProjectResourcePermissions.of(false)
                .withValidate(true).withManageSettings(true).withDelete(true);

        assertThat(permissions.canEdit()).isFalse();
        assertThat(permissions.canDelete()).isTrue();
        assertThat(permissions.canValidate()).isTrue();
        assertThat(permissions.canManageSettings()).isTrue();
    }
}
