package fr.siamois.domain.services.authorization;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import org.springframework.stereotype.Service;

/**
 * Checks user permissions against the profile-based permission system.
 * <p>
 * A permission is granted when one of the profiles assigned to the person
 * ({@code PersonProfileAssignment}) contains it within a matching scope.
 * Scopes cascade: an INSTANCE-scoped profile grants the permission everywhere,
 * an ORGANISATION-scoped profile grants it within its institution, and a
 * PROJECT-scoped profile grants it on its action unit only.
 * <p>
 * Replaces the deprecated {@link PermissionService}.
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
        return hasProjectPermission(user, actionUnitId, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);
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
        return hasOrganizationPermission(person, institution, PermissionConstants.ORGANIZATION_ACCESS);
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
