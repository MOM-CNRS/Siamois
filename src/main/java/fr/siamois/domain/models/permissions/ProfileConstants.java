package fr.siamois.domain.models.permissions;

public final class ProfileConstants {

    /**
     * The superadmin profiles allows the user to create members and organizations
     */
    public static final String SUPERADMIN = "SUPERADMIN";

    /**
     * The organization manager is allowed to manage members, projects, places, and data of their organization.
     * This profile must be attached with an {@link fr.siamois.domain.models.institution.Institution}
     */
    public static final String ORGANIZATION_MANAGER = "ORGANIZATION_MANAGER";

    /**
     * The organization project manager is allowed to manage projects, places, and data of their organization.
     * This profile must be attached with an {@link fr.siamois.domain.models.institution.Institution}
     */
    public static final String ORGANIZATION_PROJECT_MANAGER = "ORGANIZATION_PROJECT_MANAGER";

    /**
     * The organization member is allowed to read all data of their organization
     * This profile must be attached with an {@link fr.siamois.domain.models.institution.Institution}
     */
    public static final String ORGANIZATION_MEMBER = "ORGANIZATION_MEMBER";

    /**
     * The project manager is allowed to manage the members of a project, read and write all data in the project.
     * This profile must be attached with an {@link fr.siamois.domain.models.institution.Institution} and an {@link fr.siamois.domain.models.actionunit.ActionUnit}
     */
    public static final String PROJECT_MANAGER = "PROJECT_MANAGER";

    /**
     * The project manager is allowed to read and write all data in the project.
     * This profile must be attached with an {@link fr.siamois.domain.models.institution.Institution} and an {@link fr.siamois.domain.models.actionunit.ActionUnit}
     */
    public static final String PROJECT_MEMBER = "PROJECT_MEMBER";

    private ProfileConstants() {
        throw new UnsupportedOperationException("ProfileConstants should not be instantiated");
    }

}
