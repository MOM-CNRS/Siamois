package fr.siamois.domain.services;

import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;

import java.util.List;

/**
 * Read/write access to the application's (instance-wide) user accounts and the profiles that can be
 * assigned to them.
 * <p>
 * Unlike {@link OrganizationMembersServiceInterface} and {@link ProjectMembersServiceInterface},
 * there is no owning entity: this is the software instance's own user management. Until the
 * corresponding tables exist, the only implementation is an in-memory mock — beans should depend
 * on this interface rather than build fake data themselves, so swapping in a real, database-backed
 * implementation later is a one-class change.
 */
public interface ApplicationMembersServiceInterface {

    /**
     * Finds every user account of the application, with the profile(s) currently assigned to each.
     *
     * @return the application's members, possibly empty
     */
    List<ApplicationMemberDTO> findMembers();

    /**
     * Finds the profiles that can be assigned to an application member.
     *
     * @return the available profiles, possibly empty
     */
    List<ProfileDTO> findAvailableProfiles();

    /**
     * Adds a user account to the application and assigns it the given profiles.
     * <p>
     * The person may be new (no id yet) or already exist elsewhere in the system; the given
     * profiles are assigned to that single person, not split across several members.
     *
     * @param person   the person to add, new or already existing
     * @param profiles the profiles to assign to the person
     * @return the resulting application membership
     */
    ApplicationMemberDTO addMember(PersonDTO person, List<ProfileDTO> profiles);

}