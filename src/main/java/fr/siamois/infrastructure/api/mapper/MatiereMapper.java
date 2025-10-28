package fr.siamois.infrastructure.api.mapper;


import fr.siamois.domain.models.vocabulary.Concept;
import fr.siamois.domain.models.vocabulary.label.ConceptLabel;
import fr.siamois.infrastructure.api.dto.MatiereDTO;

import java.util.List;
import java.util.stream.Collectors;

public final class MatiereMapper {

    private MatiereMapper() {}

    public static MatiereDTO toDTO(ConceptLabel label) {
        var concept = label.getConcept();
        return new MatiereDTO(
                concept.getId(),
                label.getValue(),            // valeur du label dans la bonne langue
                concept.getExternalId(),     // code ou identifiant externe
                List.of()                    // parents (à compléter plus tard si besoin)
        );
    }
}