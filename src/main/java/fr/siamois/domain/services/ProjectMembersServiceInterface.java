package fr.siamois.domain.services;

import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import fr.siamois.dto.entity.ProjectMemberDTO;

import java.util.List;

/**
 * Read/write access to a project's members and the profiles that can be assigned to them.
 * <p>
 * Until the corresponding tables exist, the only implementation is an in-memory mock — beans should
 * depend on this interface rather than build fake data themselves, so swapping in a real,
 * database-backed implementation later is a one-class change.
 */
public interface ProjectMembersServiceInterface {

    /**
     * Finds every member of the given project, with the profile(s) currently assigned to each.
     *
     * @param project the project whose members are requested
     * @return the project's members, possibly empty
     */
    List<ProjectMemberDTO> findMembersOf(ActionUnitDTO project);

    /**
     * Finds the profiles that can be assigned to a member of the given project.
     *
     * @param project the project whose assignable profiles are requested
     * @return the available profiles, possibly empty
     */
    List<ProfileDTO> findAvailableProfiles(ActionUnitDTO project);

    /**
     * Adds a member to the given project and assigns it the given profiles.
     * <p>
     * The person may be new (no id yet) or already exist elsewhere in the system; the given
     * profiles are assigned to that single person, not split across several members.
     *
     * @param project  the project the person is added to
     * @param person   the person to add, new or already existing
     * @param profiles the profiles to assign to the person within this project
     * @return the resulting project membership
     */
    ProjectMemberDTO addMemberToProject(ActionUnitDTO project, PersonDTO person, List<ProfileDTO> profiles);

    /**
     * Removes a member from the given project.
     *
     * @param project the project the person is removed from
     * @param member  the member to remove
     */
    void removeMemberFromProject(ActionUnitDTO project, ProjectMemberDTO member);

    /**
     * Assigns a profile to a project member.
     *
     * @param project the project the member belongs to
     * @param member  the member the profile is assigned to
     * @param profile the profile to assign
     */
    void addProfileToMember(ActionUnitDTO project, ProjectMemberDTO member, ProfileDTO profile);

    /**
     * Unassigns a profile from a project member.
     * <p>
     * Refuses to remove the {@code PROJECT_MANAGER} profile from the last remaining manager of
     * the project.
     *
     * @param project the project the member belongs to
     * @param member  the member the profile is unassigned from
     * @param profile the profile to unassign
     * @return true if the profile was actually unassigned, false if the removal was refused
     */
    boolean removeProfileFromMember(ActionUnitDTO project, ProjectMemberDTO member, ProfileDTO profile);

}