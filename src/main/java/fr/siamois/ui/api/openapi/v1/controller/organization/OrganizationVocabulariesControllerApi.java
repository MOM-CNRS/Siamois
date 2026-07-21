package fr.siamois.ui.api.openapi.v1.controller.organization;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.VocabularyOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = OpenApiTags.ORGANISATION)
@RequiredArgsConstructor
public class OrganizationVocabulariesControllerApi {

    private static final String HEADER_TOTAL_COUNT = "X-Total-Count";

    private final ProjectApiService projectApiService;
    private final VocabularyOpenApiService vocabularyOpenApiService;

    @GetMapping("/{id}/vocabularies")
    @Operation(
            summary = "Vocabulaires d'une organisation pour les formulaires",
            description = "Retourne le catalogue des thésaurus et les listes de concepts par field_code "
                    + "(configuration institution / utilisateur), nécessaires aux formulaires."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.VOCABULARY)
    public ResponseEntity<VocabulariesResponse> getVocabularies(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        VocabulariesResponse body = vocabularyOpenApiService.listOrganizationVocabularies(caller, id, lang);
        long totalConcepts = body.getData().vocabulariesByFieldCode().values().stream()
                .mapToLong(List::size)
                .sum();
        return ResponseEntity.ok()
                .header(HEADER_TOTAL_COUNT, String.valueOf(totalConcepts))
                .body(body);
    }
}
