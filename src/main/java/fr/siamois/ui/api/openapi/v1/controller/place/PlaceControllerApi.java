package fr.siamois.ui.api.openapi.v1.controller.place;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.ui.api.openapi.v1.service.FieldQueryService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.SpatialUnitDTO;
import fr.siamois.ui.api.openapi.v1.request.project.ProjectListFilter;
import fr.siamois.ui.api.openapi.v1.response.project.ProjectListResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceListResponse;
import fr.siamois.ui.api.openapi.v1.service.ProjectListAssembler;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.data.domain.Page;
import org.springframework.util.MultiValueMap;

import fr.siamois.ui.api.openapi.v1.response.SiblingsResponse;

import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.place.PlaceCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.place.PlacePatchRequest;
import fr.siamois.ui.api.openapi.v1.response.find.FindListResponse;
import fr.siamois.ui.api.openapi.v1.response.place.PlaceCreatedResponse;
import fr.siamois.ui.api.openapi.v1.response.recordingunit.RecordingUnitListResponse;
import fr.siamois.ui.api.openapi.v1.response.spatialunit.PlaceResponse;
import fr.siamois.ui.api.openapi.v1.service.PlaceOpenApiService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Hidden;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/places")
@Tag(name = OpenApiTags.SPATIAL_UNIT)
@RequiredArgsConstructor
public class PlaceControllerApi {

    private final ProjectApiService projectApiService;
    private final PlaceOpenApiService placeOpenApiService;
    private final ProjectListAssembler projectListAssembler;
    private final FieldQueryService fieldQueryService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer un lieu",
            description = "Crée une unité spatiale dans une organisation. "
                    + "Champs obligatoires : organizationId, name, typeConceptId (concept SIASU.TYPE). "
                    + "Droit requis : gestionnaire d'institution ou gestionnaire d'action."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre ou création non autorisée"),
            @ApiResponse(responseCode = "404", description = "Organisation ou type introuvable"),
            @ApiResponse(responseCode = "409", description = "Un lieu avec ce nom existe déjà dans l'organisation"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PlaceCreatedResponse> create(
            @RequestBody PlaceCreateRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PlaceCreatedResponse.PlaceCreatedItem item =
                placeOpenApiService.createPlace(caller, request, lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PlaceCreatedResponse(item));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Modifier un lieu",
            description = "Mise à jour partielle : name, typeConceptId, address. "
                    + "Champs absents ou null = inchangés. "
                    + "Droit requis : gestionnaire d'institution ou gestionnaire d'action."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Modifié"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre ou modification non autorisée"),
            @ApiResponse(responseCode = "404", description = "Lieu ou type introuvable"),
            @ApiResponse(responseCode = "409", description = "Un lieu avec ce nom existe déjà dans l'organisation"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PlaceCreatedResponse> patch(
            @PathVariable Long id,
            @RequestBody PlacePatchRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PlaceCreatedResponse.PlaceCreatedItem item =
                placeOpenApiService.updatePlace(caller, id, request, lang);
        return ResponseEntity.ok(new PlaceCreatedResponse(item));
    }

    @PostMapping(value = "/{id}/duplicate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Dupliquer un lieu",
            description = "Copie les champs et les parents du lieu (pas ses enfants), nommée « nom (n) ». "
                    + "Droit requis : gestion des lieux de l'organisation."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Copie créée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre ou duplication non autorisée"),
            @ApiResponse(responseCode = "404", description = "Lieu introuvable"),
            @ApiResponse(responseCode = "409", description = "Aucun nom libre pour la copie"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<PlaceCreatedResponse> duplicate(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PlaceCreatedResponse.PlaceCreatedItem item = placeOpenApiService.duplicatePlace(caller, id, lang);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PlaceCreatedResponse(item));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Supprimer un lieu",
            description = "Uniquement si le lieu n'est pas référencé (enfants, UE, projets, contenants). "
                    + "Droit requis : gestionnaire d'institution ou gestionnaire d'action."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Supprimé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre ou suppression non autorisée"),
            @ApiResponse(responseCode = "404", description = "Lieu introuvable"),
            @ApiResponse(responseCode = "409", description = "Lieu non supprimable (références existantes)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {

        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        placeOpenApiService.deletePlace(caller, id, lang);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "Lieux contenus dans un lieu",
            description = "Lieux enfants directs (hiérarchie des lieux), même contrat que GET /api/v1/places : "
                    + "pagination, tri (name, id, code, creationTime), recherche sur name.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Pagination ou tri invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Lieu introuvable ou hors périmètre")
    })
    public ResponseEntity<PlaceListResponse> getChildren(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name:asc") String sort,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        PlaceListResponse body = placeOpenApiService.listChildren(caller, id, offset, limit, sort, search, lang);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(body.getMeta().total()))
                .body(body);
    }

    @GetMapping("/{id}/projects")
    @Operation(summary = "Projets d'un lieu",
            description = "Projets accessibles dont le contexte spatial contient ce lieu — même contrat et même "
                    + "réponse que GET /api/v1/projects (pagination, tri, search, filtres f.<clé>, fields), "
                    + "avec f.spatialContext imposé à ce lieu.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "Paramètres de pagination, tri ou filtre invalides"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Lieu introuvable ou hors périmètre")
    })
    public ResponseEntity<ProjectListResponse> getProjects(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name:asc") String sort,
            @RequestParam(required = false) String fields,
            @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> queryParams,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        projectApiService.validatePagedListRequest(offset, limit);
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        SpatialUnitDTO place = placeOpenApiService.requireAccessible(caller, id);
        ProjectListFilter filter = ProjectListFilter.parse(queryParams).withSpatialContext(place.getId());
        Page<AccessibleProjectForApi> rows = projectApiService.pageAccessibleProjects(
                caller, place.getCreatedByInstitution().getId(), search, offset, limit, sort, filter,
                fieldQueryService.parse(ActionUnit.class, queryParams, sort, acceptLanguage));
        ProjectListResponse body = projectListAssembler.assemble(caller, rows, fields, lang, limit, offset);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(rows.getTotalElements()))
                .body(body);
    }

    @GetMapping("/{id}/siblings")
    @Operation(summary = "Lieu précédent et suivant",
            description = "Voisins dans la même organisation, par ordre de création. Boucle en fin de liste (le suivant du "
                    + "dernier est le premier) ; null seulement s'il n'y a aucun autre élément.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<SiblingsResponse> getSiblings(@PathVariable Long id) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        return ResponseEntity.ok(new SiblingsResponse(placeOpenApiService.findSiblings(caller, id)));
    }

    @Operation(
            summary = "Un lieu via son identifiant",
            description = "Valeurs des champs formulaire (SpatialUnit.DETAILS_FORM, statique — pas de "
                    + "catalogue de types par lieu), indexées par fieldId, plus le layout et le catalogue "
                    + "de champs pour la fiche React."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Lieu introuvable ou hors périmètre"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PlaceResponse> getById(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return ResponseEntity.ok(new PlaceResponse(placeOpenApiService.getPlaceById(caller, id, lang)));
    }

    @Hidden
    @Operation(summary = "La liste des mobiliers d'un lieu")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/mobiliers")
    @Tag(name = "Mobilier")
    public ResponseEntity<FindListResponse> getFinds(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }

    @Hidden
    @Operation(summary = "Récupérer la liste paginée des unités d'enregistrement d'un projet")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "404", description = "Institution non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    @GetMapping("/{id}/recording-units")
    @Tag(name = "Unité d'enregistrement")
    public ResponseEntity<RecordingUnitListResponse> getList(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit) {

        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet");
    }
}
