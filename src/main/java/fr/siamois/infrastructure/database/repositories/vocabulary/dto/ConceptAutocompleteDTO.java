package fr.siamois.infrastructure.database.repositories.vocabulary.dto;

import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import lombok.AccessLevel;
import lombok.Builder;

import java.util.List;

@Builder(access = AccessLevel.PUBLIC)
public record ConceptAutocompleteDTO(
        ConceptLabel conceptLabelToDisplay,
        String originalPrefLabel,
        List<String> altLabels,
        String definition,
        String hierarchyPrefLabel
) {}
