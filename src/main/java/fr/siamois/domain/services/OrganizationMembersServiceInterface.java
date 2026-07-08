package fr.siamois.domain.services;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read/write access to an institution's members and the profiles that can be assigned to them.
 * <p>
 * Until the corresponding tables exist, the only implementation is an in-memory mock — beans should
 * depend on this interface rather than build fake data themselves, so swapping in a real,
 * database-backed implementation later is a one-class change.
 */
@Service
public interface OrganizationMembersServiceInterface {

    /**
     * Finds every member of the given institution, with the profile(s) currently assigned to each.
     *
     * @param institution the institution whose members are requested
     * @return the institution's members, possibly empty
     */
    List<InstitutionMemberDTO> findMembersOf(InstitutionDTO institution);

    /**
     * Finds the profiles that can be assigned to a member of the given institution.
     *
     * @param institution the institution whose assignable profiles are requested
     * @return the available profiles, possibly empty
     */
    List<ProfileDTO> findAvailableProfiles(InstitutionDTO institution);

    /**
     * Adds a member to the given institution and assigns it the given profiles.
     * <p>
     * profiles are assigned to that single person
     *
     * @param institution the institution the person is added to
     * @param person      the person to add, new or already existing
     * @param profiles    the profiles to assign to the person within this institution
     * @return the resulting institution membership
     */
    InstitutionMemberDTO addMemberToInstitution(InstitutionDTO institution, PersonDTO person, List<ProfileDTO> profiles);

    /**
     * Removes a member from the given institution.
     *
     * @param institution the institution the person is removed from
     * @param member      the member to remove
     */
    void removeMemberFromInstitution(InstitutionDTO institution, InstitutionMemberDTO member);

    /**
     * Assigns a profile to an institution member.
     *
     * @param institution the institution the member belongs to
     * @param member      the member the profile is assigned to
     * @param profile     the profile to assign
     */
    void addProfileToMember(InstitutionDTO institution, InstitutionMemberDTO member, ProfileDTO profile);

    /**
     * Unassigns a profile from an institution member.
     * <p>
     * Refuses to remove the {@code ORGANIZATION_MANAGER} profile from the last remaining manager
     * of the institution.
     *
     * @param institution the institution the member belongs to
     * @param member      the member the profile is unassigned from
     * @param profile     the profile to unassign
     * @return true if the profile was actually unassigned, false if the removal was refused
     */
    boolean removeProfileFromMember(InstitutionDTO institution, InstitutionMemberDTO member, ProfileDTO profile);

}