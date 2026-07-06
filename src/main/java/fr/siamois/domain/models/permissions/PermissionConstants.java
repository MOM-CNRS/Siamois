package fr.siamois.domain.models.permissions;

/**
 * This class contains all the system associated code of permissions.
 * It should only contain constant strings
 */
public final class PermissionConstants {

    // Instance SCOPE
    public static final String INSTANCE_MANAGE_MEMBERS = "INSTANCE_MANAGE_MEMBERS";

    // ORGANIZATION SCOPE
    /**
     * Allows the user to create new organizations
     */
    public static final String ORGANIZATION_CREATE = "ORGANIZATION_CREATE";

    /**
     * Allows the user to manage the member of the specified organization
     */
    public static final String ORGANIZATION_MANAGE_MEMBERS = "ORGANIZATION_MANAGE_MEMBERS";

    /**
     * Allows the user to create new action units in specified organization
     */
    public static final String ORGANIZATION_CREATE_ACTIONS = "ORGANIZATION_CREATE_ACTIONS";

    /**
     * Allows the user to create spatial unit in the specified organization
     */
    public static final String ORGANIZATION_CREATE_PLACES = "ORGANIZATION_CREATE_PLACES";

    /**
     * Allows the user to access all of the organization data
     */
    public static final String ORGANIZATION_ACCESS = "ORGANIZATION_ACCESS";

    // Project SCOPE

    public static final String PROJECT_MANAGE_MEMBERS = "PROJET_MANAGE_MEMBERS";
    public static final String PROJECT_EDIT_RECORDING_UNITS = "PROJECT_EDIT_RECORDING_UNITS";
    public static final String PROJECT_EDIT_PHASES = "PROJECT_EDIT_PHASES";

    private PermissionConstants() {
        throw new UnsupportedOperationException("PermissionConstants should never be instantiated");
    }

}
