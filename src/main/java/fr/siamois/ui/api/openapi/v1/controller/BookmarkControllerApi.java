package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.BookmarkService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.request.bookmark.BookmarkCreateRequest;
import fr.siamois.ui.api.openapi.v1.request.bookmark.BookmarkRenameRequest;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
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

/**
 * Generic bookmark create/delete — not Project-specific (plan §5), a thin wrapper around the
 * existing {@link BookmarkService}. The client always knows the current bookmarked state (it's
 * read off {@code ProjectResource.bookmarked} etc.), so there's no toggle endpoint: it calls
 * create or delete explicitly.
 */
@RestController
@RequestMapping("/api/v1/bookmarks")
@Tag(name = OpenApiTags.BOOKMARK)
@RequiredArgsConstructor
public class BookmarkControllerApi {

    private final ProjectApiService projectApiService;
    private final BookmarkService bookmarkService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ajouter un favori",
            description = "resourceUri générique (ex. /action-unit/123), pas le chemin REST. "
                    + "organizationId doit être dans le périmètre JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Créé"),
            @ApiResponse(responseCode = "400", description = "resourceUri ou organizationId manquant"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> create(
            @RequestBody BookmarkCreateRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        if (body.resourceUri() == null || body.resourceUri().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceUri est obligatoire");
        }
        if (body.organizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        UserInfo userInfo = requireUserInfo(body.organizationId(), acceptLanguage);
        bookmarkService.save(userInfo, body.resourceUri(), body.titleCode());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** A bookmark as the home page lists it. */
    public record BookmarkItem(Long id, String title, String resourceUri) {
    }

    /** Body of {@link #latest}. */
    public record BookmarkListResponse(java.util.List<BookmarkItem> data) {
    }

    @GetMapping
    @Operation(summary = "Derniers favoris",
            description = "Les favoris de l'utilisateur dans l'organisation, le plus récent d'abord (50 au plus).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public BookmarkListResponse latest(
            @RequestParam Long organizationId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        UserInfo userInfo = requireUserInfo(organizationId, acceptLanguage);
        return new BookmarkListResponse(bookmarkService.latest(userInfo, limit).stream()
                .map(b -> new BookmarkItem(b.getId(), b.getTitle(), b.getResourceUri()))
                .toList());
    }

    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Renommer un favori",
            description = "Le nom d'un favori est le sien : il ne suit plus la page ou la vue vers laquelle il pointe. "
                    + "organizationId doit être dans le périmètre JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Renommé"),
            @ApiResponse(responseCode = "400", description = "resourceUri, title ou organizationId manquant"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation non accessible"),
            @ApiResponse(responseCode = "404", description = "Pas de favori pour cette ressource")
    })
    public ResponseEntity<Void> rename(
            @RequestBody BookmarkRenameRequest body,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        if (body.resourceUri() == null || body.resourceUri().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceUri est obligatoire");
        }
        if (body.title() == null || body.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title est obligatoire");
        }
        if (body.organizationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId est obligatoire");
        }
        UserInfo userInfo = requireUserInfo(body.organizationId(), acceptLanguage);
        if (!bookmarkService.rename(userInfo, body.resourceUri(), body.title())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun favori pour cette ressource");
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Retirer un favori",
            description = "Même resourceUri que celui utilisé à la création. organizationId doit être dans le périmètre JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Retiré (ou déjà absent)"),
            @ApiResponse(responseCode = "400", description = "resourceUri ou organizationId manquant"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation non accessible"),
            @ApiResponse(responseCode = "500", description = "Erreur interne")
    })
    public ResponseEntity<Void> delete(
            @RequestParam String resourceUri,
            @RequestParam Long organizationId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        if (resourceUri == null || resourceUri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceUri est obligatoire");
        }
        UserInfo userInfo = requireUserInfo(organizationId, acceptLanguage);
        bookmarkService.deleteBookmark(userInfo, resourceUri);
        return ResponseEntity.noContent().build();
    }

    /** Body of {@link #status}. */
    public record BookmarkStatusResponse(boolean bookmarked) {
    }

    @GetMapping("/status")
    @Operation(summary = "Savoir si une ressource est en favori",
            description = "Pour les pages sans ressource REST qui porte déjà ce drapeau (listes, accueil) : "
                    + "une entité, elle, l'expose directement dans son champ bookmarked. "
                    + "organizationId doit être dans le périmètre JWT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "400", description = "resourceUri absent"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public ResponseEntity<BookmarkStatusResponse> status(
            @RequestParam String resourceUri,
            @RequestParam Long organizationId,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        if (resourceUri == null || resourceUri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resourceUri est obligatoire");
        }
        UserInfo userInfo = requireUserInfo(organizationId, acceptLanguage);
        return ResponseEntity.ok(new BookmarkStatusResponse(
                Boolean.TRUE.equals(bookmarkService.isRessourceBookmarkedByUser(userInfo, resourceUri))));
    }

    private UserInfo requireUserInfo(Long organizationId, String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = projectApiService.requireOrganization(organizationId, caller);
        String lang = ProjectApiService.primaryAcceptLanguage(acceptLanguage);
        return new UserInfo(institution, caller.person(), lang);
    }
}
