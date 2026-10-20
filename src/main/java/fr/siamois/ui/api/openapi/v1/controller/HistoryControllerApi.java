package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.domain.models.UserHistoryEntry;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.UserHistoryService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.OpenApiTags;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The pages the current user opened lately in an organisation (the sidebar's history, kept in base). */
@RestController
@RequestMapping("/api/v1/history")
@Tag(name = OpenApiTags.HISTORY)
@RequiredArgsConstructor
public class HistoryControllerApi {

    private final ProjectApiService projectApiService;
    private final UserHistoryService historyService;

    /** The main pane's page, and the overview open next to it (secondary fields null when there was none). */
    public record HistoryItem(Long id, String title, String icon, String mainUri, String mainTitle,
                              String secondaryUri, String secondaryTitle) {
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
        return new HistoryListResponse(historyService.latest(userInfo, limit).stream().map(HistoryControllerApi::item).toList());
    }

    private static HistoryItem item(UserHistoryEntry e) {
        boolean overview = !e.getSecondaryUri().isEmpty();
        return new HistoryItem(e.getId(),
                overview ? e.getSecondaryTitle() : e.getMainTitle(),
                overview ? e.getSecondaryIcon() : e.getMainIcon(),
                e.getMainUri(), e.getMainTitle(),
                overview ? e.getSecondaryUri() : null, overview ? e.getSecondaryTitle() : null);
    }
}
