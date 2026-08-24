package fr.siamois.domain.models.permissions;

/**
 * This class contains all the system associated code of permissions.
 * It should only contain constant strings
 */
public final class PermissionConstants {

    // Instance SCOPE
    public static final String INSTANCE_MANAGE_SETTINGS = "INSTANCE_MANAGE_SETTINGS";

    // ORGANIZATION SCOPE
    /**
     * Allows the user to create new {@link fr.siamois.domain.models.institution.Institution}
     */
    public static final String ORGANIZATION_CREATE = "ORGANIZATION_CREATE";

    /**
     * Allows the user to manage the settings of the specified {@link fr.siamois.domain.models.institution.Institution}:
     * its {@link fr.siamois.domain.models.auth.Person} members, name, thesaurus configuration, etc.
     */
    public static final String ORGANIZATION_MANAGE_SETTINGS = "ORGANIZATION_MANAGE_SETTINGS";

    /**
     * Allows the user to manage new {@link fr.siamois.domain.models.actionunit.ActionUnit} in specified {@link fr.siamois.domain.models.institution.Institution}
     */
    public static final String ORGANIZATION_MANAGE_ACTIONS = "ORGANIZATION_MANAGE_ACTIONS";

    /**
     * Allows the user to manage {@link fr.siamois.domain.models.spatialunit.SpatialUnit} in the specified {@link fr.siamois.domain.models.institution.Institution}
     */
    public static final String ORGANIZATION_MANAGE_PLACES = "ORGANIZATION_MANAGE_PLACES";

    /**
     * Allows the user to access all of the {@link fr.siamois.domain.models.institution.Institution} data
     */
    public static final String ORGANIZATION_ACCESS = "ORGANIZATION_ACCESS";

    // Project SCOPE

    /**
     * Allows the user to manage the settings of the specified {@link fr.siamois.domain.models.actionunit.ActionUnit}:
     * its members, thesaurus configuration, table fields/types, identifier format, etc.
     */
    public static final String PROJECT_MANAGE_SETTINGS = "PROJECT_MANAGE_SETTINGS";
    public static final String PROJECT_EDIT_RECORDING_UNITS = "PROJECT_EDIT_RECORDING_UNITS";
    public static final String PROJECT_EDIT_PHASES = "PROJECT_EDIT_PHASES";
    public static final String PROJECT_EDIT_FINDS = "PROJECT_EDIT_FINDS";
    public static final String PROJECT_EDIT_CONTAINERS = "PROJECT_EDIT_CONTAINERS";

    private PermissionConstants() {
        throw new UnsupportedOperationException("PermissionConstants should never be instantiated");
    }

}
