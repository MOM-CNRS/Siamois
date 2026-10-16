package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.permissions.PermissionConstants;
import fr.siamois.domain.services.permissions.ProfilePermissionService;
import fr.siamois.dto.entity.InstitutionDTO;
import fr.siamois.dto.entity.PersonDTO;
import fr.siamois.mapper.InstitutionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Whether a person may edit the entity at one end of a link: editing a link is editing that entity's
 * data, so it takes the same permission as editing the entity (the project, organisation or instance one).
 * A person is never edited through a link.
 */
@Service
@RequiredArgsConstructor
public class RelationWriteAuthorizer {

    private final ProfilePermissionService profilePermissionService;
    private final InstitutionMapper institutionMapper;

    /** The permission codes that grant editing a kind of entity: instance, organisation, then project. */
    private record Codes(String instance, String organization, String project) {
    }

    private static final Codes RECORDING_UNITS = new Codes(PermissionConstants.INSTANCE_EDIT_RECORDING_UNITS,
            PermissionConstants.ORGANIZATION_EDIT_RECORDING_UNITS, PermissionConstants.PROJECT_EDIT_RECORDING_UNITS);
    private static final Codes FINDS = new Codes(PermissionConstants.INSTANCE_EDIT_FINDS,
            PermissionConstants.ORGANIZATION_EDIT_FINDS, PermissionConstants.PROJECT_EDIT_FINDS);
    private static final Codes PHASES = new Codes(PermissionConstants.INSTANCE_EDIT_PHASES,
            PermissionConstants.ORGANIZATION_EDIT_PHASES, PermissionConstants.PROJECT_EDIT_PHASES);
    private static final Codes CONTAINERS = new Codes(PermissionConstants.INSTANCE_EDIT_CONTAINERS,
            PermissionConstants.ORGANIZATION_EDIT_CONTAINERS, PermissionConstants.PROJECT_EDIT_CONTAINERS);
    private static final Codes DOCUMENTS = new Codes(PermissionConstants.INSTANCE_EDIT_DOCUMENTS,
            PermissionConstants.ORGANIZATION_EDIT_DOCUMENTS, PermissionConstants.PROJECT_EDIT_DOCUMENTS);
    private static final Codes STUDIES = new Codes(PermissionConstants.INSTANCE_EDIT_STUDIES,
            PermissionConstants.ORGANIZATION_EDIT_STUDIES, PermissionConstants.PROJECT_EDIT_STUDIES);

    public boolean canEdit(RelationEndpoint endpoint, PersonDTO person) {
        if (endpoint == null || person == null || person.getId() == null) return false;
        InstitutionDTO institution = institutionOf(endpoint);
        return switch (endpoint.kind()) {
            case RECORDING_UNIT -> byProject(endpoint, person, institution, RECORDING_UNITS);
            case SPECIMEN -> byProject(endpoint, person, institution, FINDS);
            case PHASE -> byProject(endpoint, person, institution, PHASES);
            case CONTAINER -> byProject(endpoint, person, institution, CONTAINERS);
            case DOCUMENT -> byProject(endpoint, person, institution, DOCUMENTS);
            case STUDY -> byProject(endpoint, person, institution, STUDIES);
            case ACTION_UNIT -> profilePermissionService.hasActionUnitWritePermission(person, institution, endpoint.id());
            case SPATIAL_UNIT -> profilePermissionService.hasInstancePermission(person, PermissionConstants.INSTANCE_MANAGE_ORGANIZATIONS_PLACES)
                    || profilePermissionService.hasOrganizationPermission(person, institution, PermissionConstants.ORGANIZATION_MANAGE_PLACES);
            case PERSON -> false;
        };
    }

    private boolean byProject(RelationEndpoint endpoint, PersonDTO person, InstitutionDTO institution, Codes codes) {
        if (institution == null) return false;
        return profilePermissionService.hasProjectPermission(new UserInfo(institution, person, null),
                endpoint.projectId(), codes.instance(), codes.organization(), codes.project());
    }

    private InstitutionDTO institutionOf(RelationEndpoint endpoint) {
        Institution institution = institutionEntityOf(endpoint.entity());
        return institution != null ? institutionMapper.convert(institution) : null;
    }

    private static Institution institutionEntityOf(Object entity) {
        return entity instanceof fr.siamois.domain.models.TraceableEntity traceable ? traceable.getCreatedByInstitution() : null;
    }
}
