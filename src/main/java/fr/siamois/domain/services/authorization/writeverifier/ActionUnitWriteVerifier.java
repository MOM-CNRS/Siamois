package fr.siamois.domain.services.authorization.writeverifier;

import fr.siamois.domain.models.TraceableEntity;
import fr.siamois.domain.models.UserInfo;
import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.dto.entity.AbstractEntityDTO;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.infrastructure.database.repositories.team.TeamMemberRepository;
import org.springframework.stereotype.Component;

@Component
public class ActionUnitWriteVerifier implements WritePermissionVerifier {

    private final TeamMemberRepository teamMemberRepository;

    public ActionUnitWriteVerifier(TeamMemberRepository teamMemberRepository) {
        this.teamMemberRepository = teamMemberRepository;
    }

    @Override
    public Class<? extends TraceableEntity> getEntityClass() {
        return ActionUnit.class;
    }

    @Override
    public boolean hasSpecificWritePermission(UserInfo userInfo, AbstractEntityDTO resource) {
        ActionUnitDTO actionUnit = (ActionUnitDTO) resource;
        /* FIXME: Being a team member does not grant write permission on the action unit. To be discussed w/ Julien about
        what he wanted to achieve here */
        return teamMemberRepository.existsByActionUnitAndPerson(actionUnit, userInfo.getUser());
    }

}
