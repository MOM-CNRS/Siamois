package fr.siamois.domain.models.permissions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

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

    /**
     * Organisation-wide counterpart of {@link #PROJECT_EDIT_RECORDING_UNITS}: allows the user to edit
     * recording units in every {@link fr.siamois.domain.models.actionunit.ActionUnit} of the institution.
     */
    public static final String ORGANIZATION_EDIT_RECORDING_UNITS = "ORGANIZATION_EDIT_RECORDING_UNITS";

    /**
     * Organisation-wide counterpart of {@link #PROJECT_EDIT_PHASES}.
     */
    public static final String ORGANIZATION_EDIT_PHASES = "ORGANIZATION_EDIT_PHASES";

    /**
     * Organisation-wide counterpart of {@link #PROJECT_EDIT_FINDS}.
     */
    public static final String ORGANIZATION_EDIT_FINDS = "ORGANIZATION_EDIT_FINDS";

    /**
     * Organisation-wide counterpart of {@link #PROJECT_EDIT_CONTAINERS}.
     */
    public static final String ORGANIZATION_EDIT_CONTAINERS = "ORGANIZATION_EDIT_CONTAINERS";

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

    // Instance-wide counterparts of the ORGANIZATION SCOPE permissions above, so the instance-scoped
    // superadmin profile can hold a properly-named code instead of the organisation one directly.

    /**
     * Instance-wide counterpart of {@link #ORGANIZATION_MANAGE_SETTINGS}: manage the settings of every
     * organisation.
     */
    public static final String INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS = "INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS";

    /**
     * Instance-wide counterpart of {@link #ORGANIZATION_MANAGE_ACTIONS}: manage the projects of every
     * organisation.
     */
    public static final String INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS = "INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS";

    /**
     * Instance-wide counterpart of {@link #ORGANIZATION_MANAGE_PLACES}: manage the spatial units of every
     * organisation.
     */
    public static final String INSTANCE_MANAGE_ORGANIZATIONS_PLACES = "INSTANCE_MANAGE_ORGANIZATIONS_PLACES";

    /**
     * Instance-wide counterpart of {@link #ORGANIZATION_ACCESS}: access the data of every organisation.
     */
    public static final String INSTANCE_ACCESS_ORGANIZATIONS = "INSTANCE_ACCESS_ORGANIZATIONS";

    /**
     * Instance-wide counterpart of {@link #PROJECT_EDIT_RECORDING_UNITS}: edit recording units in every
     * project of every organisation.
     */
    public static final String INSTANCE_EDIT_RECORDING_UNITS = "INSTANCE_EDIT_RECORDING_UNITS";

    /**
     * Instance-wide counterpart of {@link #PROJECT_EDIT_PHASES}.
     */
    public static final String INSTANCE_EDIT_PHASES = "INSTANCE_EDIT_PHASES";

    /**
     * Instance-wide counterpart of {@link #PROJECT_EDIT_FINDS}.
     */
    public static final String INSTANCE_EDIT_FINDS = "INSTANCE_EDIT_FINDS";

    /**
     * Instance-wide counterpart of {@link #PROJECT_EDIT_CONTAINERS}.
     */
    public static final String INSTANCE_EDIT_CONTAINERS = "INSTANCE_EDIT_CONTAINERS";

    private PermissionConstants() {
        throw new UnsupportedOperationException("PermissionConstants should never be instantiated");
    }

    /**
     * @return every permission code declared as a {@code public static final String} constant on this
     *         class — the single source of truth for "every known permission," used to seed the
     *         {@code permission} table. Individual profiles (see {@code ProfileService}) still only ever
     *         get granted the subset of codes matching their own scope.
     */
    public static List<String> allCodes() {
        return Arrays.stream(PermissionConstants.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && field.getType().equals(String.class))
                .map(PermissionConstants::readCode)
                .toList();
    }

    private static String readCode(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to read permission constant " + field.getName(), e);
        }
    }

}
