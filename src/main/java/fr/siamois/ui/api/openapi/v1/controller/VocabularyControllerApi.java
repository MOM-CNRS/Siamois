package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesData;
import fr.siamois.ui.api.openapi.v1.response.vocabulary.VocabulariesResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.VocabularyOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vocabularies")
@Tag(name = OpenApiTags.VOCABULARY, description = "Vocabulaires contrôlés (thésaurus par field_code)")
@RequiredArgsConstructor
public class VocabularyControllerApi {

    private final ProjectApiService projectApiService;
    private final VocabularyOpenApiService vocabularyOpenApiService;

    @GetMapping
    @Operation(
            summary = "Tous les vocabulaires configurés pour une organisation",
            description = "Retourne, pour chaque `field_code` configuré (institution ou préférence utilisateur), "
                    + "la liste des concepts disponibles (même format que `vocabulariesByFieldCode` sur les formulaires). "
                    + "Jusqu'à " + fr.siamois.domain.services.vocabulary.FieldConfigurationService.LIMIT_RESULTS
                    + " concepts par field_code. "
                    + "Paramètre `organizationId` : institution dans le périmètre JWT. "
                    + "Langue des libellés : en-tête Accept-Language."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok (map vide si aucune configuration)"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre"),
            @ApiResponse(responseCode = "404", description = "Organisation introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @Tag(name = OpenApiTags.APPLICATION_MOBILE, description = OpenApiTags.APPLICATION_MOBILE_DESCRIPTION)
    public ResponseEntity<VocabulariesResponse> listVocabularies(
            @Parameter(description = "Institution (doit être dans le périmètre JWT).", example = "10", required = true)
            @RequestParam long organizationId,
            @Parameter(description = "Langue préférée pour les libellés (première entrée utilisée).")
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        projectApiService.assertOrganizationInCallerScope(organizationId, caller.accessibleInstitutionIds());
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        VocabulariesData data = vocabularyOpenApiService.listVocabulariesForOrganization(
                organizationId, caller.person(), lang);
        return ResponseEntity.ok(new VocabulariesResponse(data));
    }
}
