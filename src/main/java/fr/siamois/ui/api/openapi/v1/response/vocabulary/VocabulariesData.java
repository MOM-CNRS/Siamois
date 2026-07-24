package fr.siamois.ui.api.openapi.v1.response.vocabulary;

import fr.siamois.infrastructure.database.repositories.vocabulary.dto.ConceptAutocompleteDTO;
import fr.siamois.ui.api.openapi.v1.resource.vocabulary.VocabularyResource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Vocabulaires configurés pour une organisation (catalogue + concepts par field_code)")
public record VocabulariesData(

        @Schema(description = "Identifiant de l'organisation")
        String organizationId,

        @Schema(description = "Codes de champs ayant un vocabulaire configuré")
        List<String> fieldCodes,

        @Schema(description = "Listes de concepts par field_code (configuration institution / utilisateur)")
        Map<String, List<ConceptAutocompleteDTO>> vocabulariesByFieldCode,

        @Schema(description = "Catalogue des thésaurus enregistrés pour l'organisation")
        List<VocabularyResource> vocabularies
) {
}
