package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.vocabulary.ConceptService;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.ActionUnitSummaryDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.ConceptMapper;
import fr.siamois.ui.api.openapi.v1.OpenApiExecutionContext;
import fr.siamois.ui.api.openapi.v1.OpenApiParamIds;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.function.BiFunction;

/**
 * The common start of creating an entity with nothing but a project and a type (phase, container,
 * study, document — {@code POST /api/v1/<entités>}), and of the identifier suggestion its creation
 * form shows ({@code GET /api/v1/identifier-suggestions}): the project in the caller's scope,
 * its organization, the caller's context there, the type.
 */
@Service
@RequiredArgsConstructor
public class ProjectTypedCreationService {

    private final ActionUnitService actionUnitService;
    private final ConceptService conceptService;
    private final ConceptMapper conceptMapper;
    private final ProfilePermissionService profilePermissionService;

    /** The project a creation targets, and the caller's context in it (its organization, their language). */
    public record ProjectContext(ActionUnitDTO project, InstitutionDTO institution, UserInfo userInfo) {
        public ActionUnitSummaryDTO projectSummary() {
            return new ActionUnitSummaryDTO(project);
        }
    }

    /** 400 when {@code projectId} is blank or the project has no organization, 404 when out of scope. */
    public ProjectContext resolveProject(String projectId, PersonDTO personDto,
                                         Set<Long> accessibleInstitutionIds, String lang) {
        ActionUnitDTO project = actionUnitService.findAccessibleProjectByKey(
                OpenApiParamIds.requireNonBlank(projectId, "projectId"), accessibleInstitutionIds).actionUnit();
        InstitutionDTO institution = project.getCreatedByInstitution();
        if (institution == null || institution.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Projet sans organisation");
        }
        return new ProjectContext(project, institution, new UserInfo(institution, personDto, lang));
    }

    /** 403 ({@code forbiddenMessage}) unless the caller holds one of the permission's three scopes on the project. */
    public void requirePermission(ProjectContext context, String forbiddenMessage,
                                  String instanceCode, String organizationCode, String projectCode) {
        if (!profilePermissionService.hasProjectPermission(context.userInfo(), context.project().getId(),
                instanceCode, organizationCode, projectCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
    }

    /** 400 when {@code typeId} is blank or not numeric, 404 ({@code notFoundMessage}) when no concept has it. */
    public ConceptDTO resolveType(String typeId, String notFoundMessage) {
        long conceptId = OpenApiParamIds.parseRequiredConceptId(typeId, "typeId");
        return conceptService.findById(conceptId)
                .map(conceptMapper::convert)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage));
    }

    /**
     * A creation form's identifier suggestion: {@code suggestion} gets the project and the type, and
     * runs in the caller's context — the identifier format is read in it. No permission is required:
     * nothing is written, and the project is already in the caller's scope.
     */
    public String suggestIdentifier(String projectId, String typeId, PersonDTO personDto,
                                    Set<Long> accessibleInstitutionIds, String lang, String typeNotFoundMessage,
                                    BiFunction<ProjectContext, ConceptDTO, String> suggestion) {
        ProjectContext context = resolveProject(projectId, personDto, accessibleInstitutionIds, lang);
        ConceptDTO type = resolveType(typeId, typeNotFoundMessage);
        return OpenApiExecutionContext.callWithUserInfo(context.userInfo(), () -> suggestion.apply(context, type));
    }
}
