package fr.siamois.domain.services.authorization.writeverifier;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.recordingunit.RecordingUnit;
import fr.siamois.domain.services.InstitutionService;
import fr.siamois.domain.services.actionunit.ActionUnitService;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.springframework.stereotype.Component;

@Component
public class RecordingUnitWriteVerifier implements WritePermissionVerifier {
    private final TeamMemberRepository teamMemberRepository;
    private final InstitutionService institutionService;
    private final ActionUnitService actionUnitService;

    public RecordingUnitWriteVerifier(TeamMemberRepository teamMemberRepository, InstitutionService institutionService, ActionUnitService actionUnitService) {
        this.teamMemberRepository = teamMemberRepository;
        this.institutionService = institutionService;
        this.actionUnitService = actionUnitService;
    }

    @Override
    public Class<? extends TraceableEntity> getEntityClass() {
        return RecordingUnit.class;
    }

    @Override
    public boolean hasSpecificWritePermission(UserInfo userInfo, TraceableEntity resource) {
        RecordingUnit recordingUnit = (RecordingUnit) resource;
        ActionUnit action = recordingUnit.getActionUnit();
        if(action == null) {
            return false;
        }
        return institutionService.isManagerOf(action.getCreatedByInstitution(),userInfo.getUser()) ||
                actionUnitService.isManagerOf(action, userInfo.getUser()) ||
                (teamMemberRepository.existsByActionUnitAndPerson(action, userInfo.getUser()) && actionUnitService.isActionUnitStillOngoing(action));
    }
}
