package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.auth.PersonName;
import fr.siamois.domain.models.exceptions.auth.InvalidNameException;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.domain.services.person.PendingAccountPersonService;
import fr.siamois.dto.api.AccessibleProjectForApi;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.ui.api.openapi.v1.request.person.PersonCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * {@code POST /api/v1/projects/{id}/persons}: creates on the fly, from a field expecting a person, someone
 * who has no account yet. The person awaits the creation of their account and joins the project as a member
 * (see {@link PendingAccountPersonService}).
 */
@Service
@RequiredArgsConstructor
public class ProjectPersonOpenApiService {

    private final ProjectApiService projectApiService;
    private final ProfilePermissionService profilePermissionService;
    private final PendingAccountPersonService pendingAccountPersonService;

    /** A right to edit some of a project's data, any of which lets the caller name a person in a field. */
    @FunctionalInterface
    private interface EditRight {
        boolean grantedTo(ProjectApiCaller caller, String projectKey, String lang);
    }

    /**
     * Creates (or reuses, when the organization already has one of this name) a person awaiting their account
     * and makes them a member of the project.
     *
     * @param caller         the authenticated caller
     * @param projectIdOrKey the project the person is named in
     * @param request        the person's first and last name
     * @param lang           the caller's language
     * @return the person, member of the project
     * @throws ResponseStatusException 404 when the project is not accessible, 403 when the caller can edit
     *                                 none of its data, 400 when a name is blank or too long
     */
    @Transactional
    public PersonDTO createPersonAwaitingAccount(ProjectApiCaller caller, String projectIdOrKey,
                                                 PersonCreateRequest request, String lang) {
        AccessibleProjectForApi row = projectApiService.requireAccessibleProject(caller, projectIdOrKey);
        ActionUnitDTO project = row.actionUnit();
        if (!canEditDataOf(caller, project, lang)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Modification des données du projet non autorisée");
        }
        return pendingAccountPersonService.createInProject(nameOf(request), project);
    }

    private boolean canEditDataOf(ProjectApiCaller caller, ActionUnitDTO project, String lang) {
        UserInfo userInfo = new UserInfo(project.getCreatedByInstitution(), caller.person(), lang);
        if (profilePermissionService.hasActionUnitWritePermission(userInfo, project)) {
            return true;
        }
        String projectKey = String.valueOf(project.getId());
        return editRights().stream().anyMatch(right -> right.grantedTo(caller, projectKey, lang));
    }

    private List<EditRight> editRights() {
        return List.of(
                projectApiService::canEditRecordingUnitsForProject,
                projectApiService::canEditFindsForProject,
                projectApiService::canEditStudiesForProject,
                projectApiService::canEditDocumentsForProject,
                projectApiService::canEditPhasesForProject,
                projectApiService::canEditContainersForProject);
    }

    private static PersonName nameOf(PersonCreateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prénom et nom requis");
        }
        try {
            return PersonName.of(request.name(), request.lastname());
        } catch (InvalidNameException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prénom et nom requis (" + e.getMessage() + ")");
        }
    }
}
