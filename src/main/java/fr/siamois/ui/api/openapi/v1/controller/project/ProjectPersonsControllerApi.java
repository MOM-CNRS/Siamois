package fr.siamois.ui.api.openapi.v1.controller.project;

import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.mapper.PersonOpenApiMapper;
import fr.siamois.ui.api.openapi.v1.request.person.PersonCreateRequest;
import fr.siamois.ui.api.openapi.v1.response.person.PersonResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectPersonOpenApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Personnes nommées dans les données d'un projet ({@code POST /api/v1/projects/{id}/persons}).
 */
@RestController
@RequestMapping("/api/v1/projects/{id}/persons")
@Tag(name = OpenApiTags.PROJECT, description = "Project persons")
@RequiredArgsConstructor
public class ProjectPersonsControllerApi {

    private final ProjectApiService projectApiService;
    private final ProjectPersonOpenApiService projectPersonOpenApiService;
    private final PersonOpenApiMapper personOpenApiMapper;

    /**
     * Crée à la volée une personne en attente de création de compte et l'ajoute aux membres du projet.
     *
     * @param id             la clé du projet
     * @param body           le prénom et le nom de la personne
     * @param acceptLanguage la langue de l'appelant
     * @return la personne créée (ou celle de même nom déjà en attente dans l'organisation)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer une personne en attente de création de compte",
            description = "Crée, à partir de son seul prénom et nom, une personne sans compte (désactivée, sans e-mail) "
                    + "et l'ajoute aux membres du projet. Si l'organisation compte déjà une personne en attente de ce nom "
                    + "(casse et accents ignorés), c'est elle qui est renvoyée et ajoutée au projet. Droit requis : "
                    + "modifier le projet ou l'une de ses données (unités d'enregistrement, mobiliers, études, documents, "
                    + "phases ou contenants).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Personne créée ou réutilisée"),
            @ApiResponse(responseCode = "400", description = "Prénom ou nom manquant ou trop long"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Modification des données du projet non autorisée"),
            @ApiResponse(responseCode = "404", description = "Projet introuvable ou non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PersonResponse> create(
            @PathVariable("id") String id,
            @RequestBody PersonCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PersonDTO person = projectPersonOpenApiService.createPersonAwaitingAccount(caller, id, body, lang);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PersonResponse(personOpenApiMapper.toResource(person, true)));
    }
}
