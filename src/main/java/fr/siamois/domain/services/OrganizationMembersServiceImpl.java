package fr.siamois.domain.services;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.InstitutionMemberDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Primary
public class OrganizationMembersServiceImpl implements OrganizationMembersServiceInterface {
    @Override
    public List<InstitutionMemberDTO> findMembersOf(InstitutionDTO institution) {
        return List.of();
    }

    @Override
    public List<ProfileDTO> findAvailableProfiles(InstitutionDTO institution) {
        return List.of();
    }

    @Override
    public InstitutionMemberDTO addMemberToInstitution(InstitutionDTO institution, PersonDTO person, List<ProfileDTO> profiles) {
        return null;
    }
}
