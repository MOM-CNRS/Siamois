package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.dto.entity.ProfileDTO;

import java.util.HashSet;
import java.util.Set;

public record PersonRole(
        PersonDTO person,
        ConceptDTO role,
        Set<ProfileDTO> profiles
) {
    public PersonRole(PersonDTO person, ConceptDTO role) {
        this(person, role, new HashSet<>());
    }
}
