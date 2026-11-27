package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.infrastructure.database.repositories.actionunit.ActionUnitRepository;
import org.springframework.lang.Nullable;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.infrastructure.database.repositories.institution.InstitutionRepository;
import fr.siamois.mapper.InstitutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Who configures the relations (docs/modele-relations.md, §3.3): the definitions and the platform default rules
 * by the platform administrator; an institution's or a project's rules with the existing right to configure that
 * institution or project.
 */
@Service
@RequiredArgsConstructor
public class RelationConfigAuthorizer {

    private final ProfilePermissionService profilePermissionService;
    private final InstitutionRepository institutionRepository;
    private final InstitutionMapper institutionMapper;
    private final ActionUnitRepository actionUnitRepository;

    public boolean canEditDefinitions(PersonDTO caller) {
        return profilePermissionService.hasInstancePermission(caller, PermissionConstants.INSTANCE_MANAGE_SETTINGS);
    }

    /**
     * Whether a person may create or change the identity of a relation, configured from a project: the platform
     * administrator always; anyone who configures the project, unless the relation is between system fields.
     *
     * @param system    the relation is between system fields (the stratigraphy)
     * @param projectId the project it is configured from, or null outside any project (the API's platform face)
     */
    public boolean canEditDefinition(boolean system, @Nullable Long projectId, PersonDTO caller) {
        if (canEditDefinitions(caller)) return true;
        if (system || projectId == null) return false;
        return actionUnitRepository.findById(projectId)
                .map(ActionUnit::getCreatedByInstitution)
                .map(institutionMapper::convert)
                .map(institution -> profilePermissionService.hasProjectSettingsPermission(
                        new UserInfo(institution, caller, null), projectId))
                .orElse(false);
    }

    public boolean canEditRules(RelationScope scope, PersonDTO caller) {
        Long institutionId = scope.institutionId();
        if (institutionId == null) return canEditDefinitions(caller);
        InstitutionDTO institution = institutionRepository.findById(institutionId)
                .map(institutionMapper::convert).orElse(null);
        if (institution == null) return false;
        Long projectId = scope.projectId();
        if (projectId == null) {
            return profilePermissionService.hasOrganizationPermission(caller, institution,
                    PermissionConstants.ORGANIZATION_MANAGE_SETTINGS);
        }
        return profilePermissionService.hasProjectSettingsPermission(new UserInfo(institution, caller, null), projectId);
    }
}
