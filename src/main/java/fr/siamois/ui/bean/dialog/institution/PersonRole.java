package fr.siamois.ui.bean.dialog.institution;

import fr.siamois.dto.entity.ConceptDTO;
import fr.siamois.dto.entity.PersonDTO;

public record PersonRole(
        PersonDTO person,
        ConceptDTO role
) {}
