package fr.siamois.ui.api.openapi.v1.controller;

import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.ui.api.openapi.v1.response.search.GlobalSearchResponse;
import fr.siamois.ui.api.openapi.v1.service.GlobalSearchService;
import fr.siamois.ui.api.openapi.v1.service.OrganizationListService;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiCaller;
import fr.siamois.ui.api.openapi.v1.service.ProjectApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Recherche globale de la barre du haut : résultats groupés par type d'entité. */
@RestController
@RequiredArgsConstructor
public class GlobalSearchControllerApi {

    private final ProjectApiService projectApiService;
    private final OrganizationListService organizationListService;
    private final GlobalSearchService globalSearchService;

    @GetMapping("/api/v1/search")
    @Tag(name = "Recherche")
    @Operation(summary = "Recherche globale groupée par type d'entité",
            description = "Quelques correspondances (limit par type) et le total de chaque type : projets, unités "
                    + "d'enregistrement, mobiliers, lieux. `q` est le texte libre ; `filters` porte les mots-clés "
                    + "`clé:valeur` (supporté : `projet:<id|identifiant|nom>`) ; `projectId` restreint à un projet "
                    + "(projet courant), sauf si un filtre `projet:` est donné. Un membre sans accès à toute "
                    + "l'organisation ne voit que ses projets.")
    @ApiResponse(responseCode = "200", description = "Ok")
    @ApiResponse(responseCode = "400", description = "organizationId absent, limit, type ou filtre invalide")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "403", description = "Organisation hors périmètre")
    public GlobalSearchResponse search(
            @Parameter(description = "Organisation (obligatoire)") @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) List<String> filters,
            @Parameter(description = "project, recordingUnit, find, place ; absent : tous")
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "5") int limit) {
        ProjectApiCaller caller = projectApiService.requireCaller();
        InstitutionDTO institution = organizationListService.requireListOrganization(caller, organizationId);
        return globalSearchService.search(caller, institution, q, projectId, filters, types, limit);
    }
}
