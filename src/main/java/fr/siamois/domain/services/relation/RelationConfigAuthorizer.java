package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.UserInfo;
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

    public boolean canEditDefinitions(PersonDTO caller) {
        return profilePermissionService.hasInstancePermission(caller, PermissionConstants.INSTANCE_MANAGE_SETTINGS);
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
