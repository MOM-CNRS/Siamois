package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;

public record PersonRole(
        PersonDTO person,
        ConceptDTO role
) {}
