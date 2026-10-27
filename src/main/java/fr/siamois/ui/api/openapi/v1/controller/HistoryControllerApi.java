package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.UserHistoryEntry;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.UserHistoryService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.service.EntityProjectResolver;
import fr.siamois.ui.api.openapi.v1.service.EntityProjectResolver.ProjectRef;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** The pages the current user opened lately in an organisation (the sidebar's history, kept in base). */
@RestController
@RequestMapping("/api/v1/history")
@Tag(name = OpenApiTags.HISTORY)
@RequiredArgsConstructor
public class HistoryControllerApi {

    private final ProjectApiService projectApiService;
    private final UserHistoryService historyService;
    private final EntityProjectResolver projectResolver;

    /**
     * The main pane's page, and the overview open next to it (secondary fields null when there was none).
     * {@code project}: the project of the fiche shown (the overview's, else the main pane's); null for a page with none.
     */
    public record HistoryItem(Long id, String title, String icon, String mainUri, String mainTitle,
                              String secondaryUri, String secondaryTitle, ProjectRef project) {
    }

    public record HistoryListResponse(List<HistoryItem> data) {
    }

    @GetMapping
    @Operation(summary = "Pages récentes",
            description = "Les dernières pages ouvertes par l'utilisateur dans l'organisation, la plus récente d'abord "
                    + "(50 au plus conservées). Une page avec un volet latéral porte aussi son URI.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ok"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    })
    public HistoryListResponse latest(
            @RequestParam Long organizationId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = projectApiService.requireOrganization(organizationId, caller);
        UserInfo userInfo = new UserInfo(institution, caller.person(), ProjectApiService.primaryAcceptLanguage(acceptLanguage));
        List<UserHistoryEntry> entries = historyService.latest(userInfo, limit);
        Map<String, ProjectRef> projects = projectResolver.resolve(
                entries.stream().flatMap(e -> java.util.stream.Stream.of(e.getMainUri(), e.getSecondaryUri())).toList(),
                caller.accessibleInstitutionIds());
        return new HistoryListResponse(entries.stream().map(e -> item(e, projects)).toList());
    }

    private static HistoryItem item(UserHistoryEntry e, Map<String, ProjectRef> projects) {
        boolean overview = !e.getSecondaryUri().isEmpty();
        return new HistoryItem(e.getId(),
                overview ? e.getSecondaryTitle() : e.getMainTitle(),
                overview ? e.getSecondaryIcon() : e.getMainIcon(),
                e.getMainUri(), e.getMainTitle(),
                overview ? e.getSecondaryUri() : null, overview ? e.getSecondaryTitle() : null,
                overview && projects.containsKey(e.getSecondaryUri()) ? projects.get(e.getSecondaryUri()) : projects.get(e.getMainUri()));
    }
}
