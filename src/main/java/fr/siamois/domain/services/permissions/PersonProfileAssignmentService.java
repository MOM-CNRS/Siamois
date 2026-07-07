package fr.siamois.domain.services.permissions;

import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.permissions.PersonProfileAssignmentRepository;
import fr.siamois.mapper.PersonMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PersonProfileAssignmentService {

    private final PersonProfileAssignmentRepository personProfileAssignmentRepository;
    private final PersonMapper personMapper;

    PersonProfileAssignmentService(PersonProfileAssignmentRepository personProfileAssignmentRepository, PersonMapper personMapper) {
        this.personProfileAssignmentRepository = personProfileAssignmentRepository;
        this.personMapper = personMapper;
    }

    public boolean assignProfile(@NonNull Profile profile, @NonNull PersonDTO personDTO) {
        Optional<PersonProfileAssignment> opt = personProfileAssignmentRepository.findByProfileAndPersonId(profile, personDTO.getId());
        if (opt.isPresent()) return false;
        PersonProfileAssignment assignment = new PersonProfileAssignment();
        assignment.setProfile(profile);
        assignment.setPerson(personMapper.invertConvert(personDTO));
        personProfileAssignmentRepository.save(assignment);
        return true;
    }

}
