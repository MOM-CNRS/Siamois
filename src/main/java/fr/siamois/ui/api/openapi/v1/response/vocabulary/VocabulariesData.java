package fr.siamois.ui.api.openapi.v1.response.vocabulary;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Vocabulaires configurés pour une organisation, indexés par field_code")
public record VocabulariesData(

        @Schema(description = "Listes de concepts par field_code (configuration institution / utilisateur)")
        Map<String, List<ConceptAutocompleteDTO>> vocabulariesByFieldCode
) {
}
