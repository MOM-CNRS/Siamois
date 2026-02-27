package fr.siamois.infrastructure.database.repositories.team;

import fr.siamois.domain.models.actionunit.ActionUnit;
import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.team.TeamMemberRelation;
import fr.siamois.dto.entity.ActionUnitDTO;
import fr.siamois.dto.entity.PersonDTO;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface TeamMemberRepository extends CrudRepository<TeamMemberRelation, TeamMemberRelation.TeamMemberId> {

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT tm.* FROM team_member tm " +
                    "JOIN action_unit au ON tm.fk_action_unit_id = au.action_unit_id " +
                    "WHERE au.fk_institution_id = :institutionId"
    )
    Set<TeamMemberRelation> findAllByInstitution(Long institutionId);

    @Query(
            nativeQuery = true,
            value = "SELECT COUNT(*) >= 1 " +
                    "FROM team_member tm " +
                    "JOIN action_unit au ON tm.fk_action_unit_id = au.action_unit_id " +
                    "WHERE au.fk_institution_id = :institutionId AND tm.fk_person_id = :personId"
    )
    boolean personIsInInstitution(Long personId, Long institutionId);

    boolean existsByActionUnitIdAndPerson(Long actionUnitId, PersonDTO person);

    Set<TeamMemberRelation> findAllByActionUnit(ActionUnit actionUnit);

    Optional<TeamMemberRelation> findByActionUnitAndPerson(ActionUnit actionUnit, Person person);
}
