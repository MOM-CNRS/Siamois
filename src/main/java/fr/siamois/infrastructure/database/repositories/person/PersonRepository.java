package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.auth.Person;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByUsernameIgnoreCase(String username);

    @Query(
            nativeQuery = true,
            value = "SELECT p.* FROM person p " +
                    "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :nameOrLastname, '%')) " +
                    "OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', :nameOrLastname, '%'))"
    )
    List<Person> findAllByNameOrLastname(String nameOrLastname);


    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT p.* " +
            "FROM person p " +
            "JOIN spatial_unit su ON su.fk_author_id = p.person_id " +
                    "WHERE su.fk_institution_id = :institutionId"
    )
    List<Person> findAllAuthorsOfSpatialUnitByInstitution(Long institutionId);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT p.* " +
                    "FROM person p " +
                    "JOIN action_unit au ON au.fk_author_id = p.person_id " +
                    "WHERE au.fk_institution_id = :institutionId"
    )
    List<Person> findAllAuthorsOfActionUnitByInstitution(Long institutionId);

    Optional<Person> findById(long id);

    @Modifying
    @Transactional
    @Query(
            nativeQuery = true,
            value = "INSERT INTO person_role_institution(fk_person_id, fk_role_concept_id, fk_institution_id) " +
                    "VALUES (:personId, :conceptId, :institutionId)"
    )
    void addPersonToInstitution(Long personId, Long institutionId, Long conceptId);

    @Query(
            nativeQuery = true,
            value = "SELECT p.* FROM person p WHERE p.is_super_admin = TRUE"
    )
    List<Person> findAllSuperAdmin();

    Optional<Person> findByEmailIgnoreCase(String email);

    @Query(
            nativeQuery = true,
            value = "SELECT p.* FROM person p " +
                    "WHERE p.mail LIKE CONCAT('%', :input, '%') " +
                    "ORDER BY similarity(p.mail, :input) DESC " +
                    "LIMIT 10"
    )
    Set<Person> findClosestByEmailLimit10(String input);

    @Query(
            nativeQuery = true,
            value = "SELECT p.* FROM person p " +
                    "WHERE p.username LIKE CONCAT('%', :input, '%') " +
                    "ORDER BY similarity(p.username, :input) DESC " +
                    "LIMIT 10"
    )
    Set<Person> findClosestByUsernameLimit10(String input);

    @Query(
            nativeQuery = true,
            value = "SELECT COUNT(DISTINCT p.person_id) " +
                    "FROM person p " +
                    "         LEFT JOIN institution_manager im ON p.person_id = im.fk_person_id AND im.fk_institution_id = :institutionId " +
                    "         LEFT JOIN action_manager am ON p.person_id = am.fk_person_id AND am.fk_institution_id = :institutionId " +
                    "         LEFT JOIN team_member tm ON p.person_id = tm.fk_person_id " +
                    "         LEFT JOIN action_unit au ON tm.fk_action_unit_id = au.action_unit_id AND au.fk_institution_id = :institutionId " +
                    "WHERE (im.fk_person_id IS NOT NULL " +
                    "    OR am.fk_person_id IS NOT NULL " +
                    "    OR au.action_unit_id IS NOT NULL) " +
                    "  AND NOT p.is_super_admin;"
    )
    long countPersonsInInstitution(Long institutionId);

    @Query(
            value = """
              select p.* 
              from person p
              inner join institution_manager im 
                     on p.person_id = im.fk_person_id
              where im.fk_institution_id = :id
              """,
            nativeQuery = true)
    Set<Person> findManagersOfInstitution(@Param("id") Long institutionId);
}
