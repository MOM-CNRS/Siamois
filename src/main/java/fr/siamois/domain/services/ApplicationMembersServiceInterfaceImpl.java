package fr.siamois.domain.services;

import fr.siamois.dto.entity.ApplicationMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class ApplicationMembersServiceInterfaceImpl implements ApplicationMembersServiceInterface {
    @Override
    public List<ApplicationMemberDTO> findMembers() {
        return List.of();
    }

    @Override
    public List<ProfileDTO> findAvailableProfiles() {
        return List.of();
    }

    @Override
    public ApplicationMemberDTO addMember(PersonDTO person, List<ProfileDTO> profiles) {
        return null;
    }
}
