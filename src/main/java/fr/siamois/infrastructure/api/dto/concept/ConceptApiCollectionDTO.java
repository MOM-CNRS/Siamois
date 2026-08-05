package fr.siamois.infrastructure.api.dto.concept;

import fr.siamois.infrastructure.api.dto.LabelDTO;

import java.util.List;

public record ConceptApiCollectionDTO(String idGroup, List<LabelDTO> labels) {
}
