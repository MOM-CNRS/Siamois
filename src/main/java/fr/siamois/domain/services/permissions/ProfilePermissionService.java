package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.dto.entity.*;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checks user permissions against the profile-based permission system.
 * <p>
 * A permission is granted when one of the profiles assigned to the person
 * ({@code PersonProfileAssignment}) contains it within a matching scope: an INSTANCE-scoped profile
 * grants it everywhere, an ORGANISATION-scoped profile grants it within its institution, and a
 * PROJECT-scoped profile grants it on its action unit only.
 * <p>
 * Every {@link PermissionConstants} code belongs to exactly one scope — a profile only ever holds codes
 * matching its own scope (see {@code ProfileService}). A capability available at more than one scope
 * (e.g. "edit recording units") therefore has one distinct code per scope
 * ({@code PROJECT_EDIT_RECORDING_UNITS} / {@code ORGANIZATION_EDIT_RECORDING_UNITS} /
 * {@code INSTANCE_EDIT_RECORDING_UNITS}), and the methods below explicitly check each scope's own code in
 * turn (instance, then organisation, then project) — see the 5-arg overload of
 * {@link #hasProjectPermission(UserInfo, Long, String, String, String)} — rather than relying on the same
 * code being reused across scopes.
 * <p>
 * Replaces the removed {@code PermissionService}.
 */
@Service
public class ProfilePermissionService {

    private final PersonProfileAssignmentRepository assignmentRepository;

    public ProfilePermissionService(PersonProfileAssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Checks if the person holds the permission through an INSTANCE-scoped profile.
     *
     * @param person         the person to check
     * @param permissionCode one of the {@link PermissionConstants} codes
     * @return true if an instance-wide profile of the person grants the permission
     */
    public boolean hasInstancePermission(PersonDTO person, String permissionCode) {
        if (person == null || person.getId() == null) {
            return false;
        }
        return assignmentRepository.personHasInstancePermission(person.getId(), permissionCode);
    }

    /**
     * Checks if the person holds the permission within the given institution,
     * either through an ORGANISATION-scoped profile on that institution or an
     * INSTANCE-scoped profile.
     *
     * @param person         the person to check
     * @param institution    the institution the action takes place in
     * @param permissionCode one of the {@link PermissionConstants} codes
     * @return true if the permission is granted in the institution
     */
    public boolean hasOrganizationPermission(PersonDTO person, InstitutionDTO institution, String permissionCode) {
        if (person == null || person.getId() == null) {
            return false;
        }
        if (hasInstancePermission(person, permissionCode)) {
            return true;
        }
        return institution != null && institution.getId() != null
                && assignmentRepository.personHasPermissionInInstitution(person.getId(), institution.getId(), permissionCode);
    }

    /**
     * Checks if the user holds the permission within their current institution.
     *
     * @param user           the user information
     * @param permissionCode one of the {@link PermissionConstants} codes
     * @return true if the permission is granted in the user's institution
     */
    public boolean hasOrganizationPermission(UserInfo user, String permissionCode) {
        return hasOrganizationPermission(user.getUser(), user.getInstitution(), permissionCode);
    }

    /**
     * Checks if the user holds the permission on the given action unit, either
     * through a PROJECT-scoped profile on that action unit, an ORGANISATION-scoped
     * profile on the user's institution, or an INSTANCE-scoped profile.
     *
     * @param user           the user information
     * @param actionUnitId   the action unit (project) the action targets; may be null
     * @param permissionCode one of the {@link PermissionConstants} codes
     * @return true if the permission is granted on the action unit
     */
    public boolean hasProjectPermission(UserInfo user, Long actionUnitId, String permissionCode) {
        if (hasOrganizationPermission(user, permissionCode)) {
            return true;
        }
        PersonDTO person = user.getUser();
        return actionUnitId != null && person != null && person.getId() != null
                && assignmentRepository.personHasPermissionInActionUnit(person.getId(), actionUnitId, permissionCode);
    }

    /**
     * Same as {@link #hasProjectPermission(UserInfo, Long, String)}, for a capability whose organisation-
     * and instance-wide counterparts are different permission codes than the project-level one (e.g.
     * {@code PROJECT_EDIT_RECORDING_UNITS} vs. {@code ORGANIZATION_EDIT_RECORDING_UNITS} vs.
     * {@code INSTANCE_EDIT_RECORDING_UNITS}) — checks instance, then organisation, then project.
     *
     * @param instanceCode     the counterpart code that grants this everywhere, held on an INSTANCE-scoped profile
     * @param organizationCode the counterpart code that grants this org-wide, held on an ORGANISATION-scoped profile
     * @param projectCode      the code granting this on the action unit itself
     */
    public boolean hasProjectPermission(UserInfo user, Long actionUnitId, String instanceCode, String organizationCode, String projectCode) {
        if (hasInstancePermission(user.getUser(), instanceCode) || hasOrganizationPermission(user, organizationCode)) {
            return true;
        }
        PersonDTO person = user.getUser();
        return actionUnitId != null && person != null && person.getId() != null
                && assignmentRepository.personHasPermissionInActionUnit(person.getId(), actionUnitId, projectCode);
    }

    /**
     * Bulk version of {@link #hasProjectPermission} : which of the given action units the user holds
     * {@code permissionCode} on, computed in at most one query total instead of one query (or up to
     * three, counting the org/instance checks) per action unit. Meant for a whole table page's worth of
     * rows at once — see {@code EntityTableViewModel#canEditByActionUnit} — rather than one row at a
     * time.
     *
     * @param actionUnitIds the action units to check, e.g. the distinct ones on the current table page
     * @return the subset of {@code actionUnitIds} the user holds {@code permissionCode} on
     */
    public Set<Long> actionUnitIdsWithPermission(UserInfo user, Collection<Long> actionUnitIds, String permissionCode) {
        return actionUnitIdsWithPermission(user, actionUnitIds, permissionCode, permissionCode);
    }

    /**
     * Same as {@link #actionUnitIdsWithPermission(UserInfo, Collection, String)}, for the rarer case
     * (e.g. {@link #hasActionUnitWritePermission}) where the organization-level short-circuit is a
     * different permission code than the project-level one.
     */
    public Set<Long> actionUnitIdsWithPermission(UserInfo user, Collection<Long> actionUnitIds,
                                                 String organizationPermissionCode, String projectPermissionCode) {
        Set<Long> ids = actionUnitIds == null ? Set.of() : actionUnitIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Set.of();
        }
        // hasProjectPermission's own org/instance short-circuit checks organizationPermissionCode; mirror
        // that here too (not just the caller's own organizationPermissionCode) so a project-scoped
        // permission granted org/instance-wide still short-circuits, exactly as it would one row at a time
        if (hasOrganizationPermission(user, organizationPermissionCode)
                || hasOrganizationPermission(user, projectPermissionCode)) {
            return ids;
        }
        PersonDTO person = user.getUser();
        if (person == null || person.getId() == null) {
            return Set.of();
        }
        return assignmentRepository.findActionUnitIdsWithPermission(person.getId(), ids, projectPermissionCode);
    }

    /**
     * Checks if the user can write the given recording unit, i.e. holds
     * {@link PermissionConstants#PROJECT_EDIT_RECORDING_UNITS} on the recording
     * unit's action unit.
     *
     * @param user          the user information
     * @param recordingUnit the recording unit to write
     * @return true if the user can write the recording unit
     */
    public boolean hasRecordingUnitWritePermission(UserInfo user, RecordingUnitDTO recordingUnit) {
        Long actionUnitId = recordingUnit.getActionUnit() != null ? recordingUnit.getActionUnit().getId() : null;
        return hasProjectPermission(user, actionUnitId,
                PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
                PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS,
                PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);
    }

    /**
     * Checks if the user can write the given specimen, i.e. holds
     * {@link PermissionConstants#PROJECT_EDIT_FINDS} on the specimen's action unit.
     *
     * @param user     the user information
     * @param specimen the specimen to write
     * @return true if the user can write the specimen
     */
    public boolean hasSpecimenWritePermission(UserInfo user, SpecimenDTO specimen) {
        Long actionUnitId = specimen.getActionUnit() != null ? specimen.getActionUnit().getId() : null;
        return hasProjectPermission(user, actionUnitId,
                PermissionConstants.INSTANCE_EDIT_FINDS,
                PermissionConstants.ORGANIZATION_EDIT_FINDS,
                PermissionConstants.PROJECT_EDIT_FINDS);
    }

    /**
     * Checks if the user can write the given phase, i.e. holds
     * {@link PermissionConstants#PROJECT_EDIT_PHASES} on the phase's action unit.
     *
     * @param user  the user information
     * @param phase the phase to write
     * @return true if the user can write the phase
     */
    public boolean hasPhaseWritePermission(UserInfo user, PhaseDTO phase) {
        Long actionUnitId = phase.getActionUnit() != null ? phase.getActionUnit().getId() : null;
        return hasProjectPermission(user, actionUnitId,
                PermissionConstants.INSTANCE_EDIT_PHASES,
                PermissionConstants.ORGANIZATION_EDIT_PHASES,
                PermissionConstants.PROJECT_EDIT_PHASES);
    }

    /**
     * Checks if the user can write the given container, i.e. holds
     * {@link PermissionConstants#PROJECT_EDIT_CONTAINERS} on the container's action unit.
     *
     * @param user      the user information
     * @param container the container to write
     * @return true if the user can write the container
     */
    public boolean hasContainerWritePermission(UserInfo user, ContainerDTO container) {
        Long actionUnitId = container.getActionUnit() != null ? container.getActionUnit().getId() : null;
        return hasProjectPermission(user, actionUnitId,
                PermissionConstants.INSTANCE_EDIT_CONTAINERS,
                PermissionConstants.ORGANIZATION_EDIT_CONTAINERS,
                PermissionConstants.PROJECT_EDIT_CONTAINERS);
    }

    /**
     * Checks if the user can write the given action unit's own fields, i.e. holds
     * {@link PermissionConstants#PROJECT_MANAGE_SETTINGS} on the action unit itself, or
     * {@link PermissionConstants#ORGANIZATION_MANAGE_ACTIONS} on the user's institution.
     *
     * @param user       the user information
     * @param actionUnit the action unit to write; its id may be null for a not-yet-created action unit
     * @return true if the user can write the action unit
     */
    public boolean hasActionUnitWritePermission(UserInfo user, ActionUnitDTO actionUnit) {
        if (hasInstancePermission(user.getUser(), PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_ACTIONS)
                || hasOrganizationPermission(user, PermissionConstants.ORGANIZATION_MANAGE_ACTIONS)) {
            return true;
        }
        return actionUnit != null && hasProjectPermission(user, actionUnit.getId(), PermissionConstants.PROJECT_MANAGE_SETTINGS);
    }

    /**
     * Checks if the person can activate the given institution, i.e. holds a profile in it or may
     * manage its settings.
     *
     * @param person      the person to check
     * @param institution the institution to activate
     * @return true if the person can activate the institution
     */
    public boolean canAccessInstitution(PersonDTO person, InstitutionDTO institution) {
        if (person == null || person.getId() == null || institution == null || institution.getId() == null) {
            return false;
        }
        return assignmentRepository.personHasAnyProfileInInstitution(person.getId(), institution.getId())
                || hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_SETTINGS)
                || hasOrganizationPermission(person, institution, PermissionConstants.ORGANIZATION_MANAGE_SETTINGS);
    }

    /**
     * Checks if the person can display the data of the given institution, i.e. holds
     * {@link PermissionConstants#ORGANIZATION_ACCESS} through an INSTANCE- or
     * ORGANISATION-scoped profile.
     *
     * @param person      the person to check
     * @param institution the institution whose data is displayed
     * @return true if the institution data can be displayed
     */
    public boolean canViewInstitutionData(PersonDTO person, InstitutionDTO institution) {
        return hasInstancePermission(person, PermissionConstants.INSTANCE_ACCESS_ORGANIZATIONS)
                || hasOrganizationPermission(person, institution, PermissionConstants.ORGANIZATION_ACCESS);
    }

    /**
     * Checks if the person can display the given project (action unit): either the
     * whole institution data is visible ({@link #canViewInstitutionData}), or the
     * person has a PROJECT-scoped profile assigned on that action unit.
     *
     * @param person       the person to check
     * @param institution  the institution owning the project
     * @param actionUnitId the project's action unit id; may be null
     * @return true if the project can be displayed
     */
    public boolean canViewProject(PersonDTO person, InstitutionDTO institution, Long actionUnitId) {
        if (canViewInstitutionData(person, institution)) {
            return true;
        }
        return actionUnitId != null && person != null && person.getId() != null
                && assignmentRepository.personHasAnyProfileOnActionUnit(person.getId(), actionUnitId);
    }

    /**
     * Checks if the person can display the given recording unit, i.e. can display
     * the project it belongs to.
     *
     * @param person        the person to check
     * @param recordingUnit the recording unit to display
     * @return true if the recording unit can be displayed
     */
    public boolean canViewRecordingUnit(PersonDTO person, RecordingUnitDTO recordingUnit) {
        Long actionUnitId = recordingUnit.getActionUnit() != null ? recordingUnit.getActionUnit().getId() : null;
        return canViewProject(person, recordingUnit.getCreatedByInstitution(), actionUnitId);
    }
}
