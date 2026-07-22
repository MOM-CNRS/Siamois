package fr.siamois.infrastructure.database.repositories.person;

import fr.siamois.domain.models.auth.Person;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByUsernameIgnoreCase(String username);

    /** Broad prefetch by firstname only — callers narrow to an exact (name, lastname) pair themselves. */
    List<Person> findAllByNameIgnoreCaseIn(Collection<String> names);

    @Query(
            nativeQuery = true,
            value = "SELECT p.* FROM person p " +
                    "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :nameOrLastname, '%')) " +
                    "OR LOWER(p.lastname) LIKE LOWER(CONCAT('%', :nameOrLastname, '%')) " +
                    "LIMIT :limit"
    )
    List<Person> findAllByNameOrLastname(String nameOrLastname, int limit);


    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT p.* " +
            "FROM person p " +
            "JOIN spatial_unit su ON su.fk_created_by = p.person_id " +
                    "WHERE su.fk_institution_id = :institutionId"
    )
    List<Person> findAllAuthorsOfSpatialUnitByInstitution(Long institutionId);

    @Query(
            nativeQuery = true,
            value = "SELECT DISTINCT p.* " +
                    "FROM person p " +
                    "JOIN action_unit au ON au.fk_created_by = p.person_id " +
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

    Optional<Person> findByEmailIgnoreCase(String email);

    Optional<Person> findByNameIgnoreCaseAndLastnameIgnoreCase(String name, String lastname);

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

    @Query("""
            SELECT COUNT(DISTINCT p.id)
            FROM PersonProfileAssignment a
            JOIN a.person p
            JOIN a.profile prof
            WHERE prof.institution.id = :institutionId
            """)
    long countPersonsInInstitution(Long institutionId);

    /**
     * Personnes rattachées à une institution, c'est-à-dire ayant au moins un profil
     * (ORGANISATION ou PROJECT) rattaché à cette institution.
     */
    @Query(
            value = """
                    SELECT DISTINCT p.*
                    FROM person p
                             JOIN person_profile_assignment ppa ON ppa.fk_person_id = p.person_id
                             JOIN profile prof ON prof.profile_id = ppa.fk_profile_id
                    WHERE prof.fk_institution_id = :institutionId
                      AND (CAST(:search AS TEXT) IS NULL
                        OR LOWER(COALESCE(p.name, '')) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%'))
                        OR LOWER(COALESCE(p.lastname, '')) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%'))
                        OR LOWER(p.mail) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%'))
                        OR LOWER(CAST(p.username AS TEXT)) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')))
                    ORDER BY p.lastname NULLS LAST, p.name NULLS LAST, p.person_id ASC
                    """,
            nativeQuery = true)
    List<Person> findAllInInstitution(@Param("institutionId") Long institutionId, @Param("search") String search);

    @Query("""
            SELECT DISTINCT p
            FROM PersonProfileAssignment a
            JOIN a.person p
            JOIN a.profile prof
            WHERE prof.institution.id = :id
              AND prof.code = fr.siamois.domain.models.permissions.ProfileConstants.ORGANIZATION_MANAGER
            """)
    Set<Person> findManagersOfInstitution(@Param("id") Long institutionId);

    @Modifying
    @Query("UPDATE Person p SET p.password = :password, p.passToModify = false WHERE p.id = :id")
    int updatePasswordById(@Param("id") Long id,
                           @Param("password") String password);
}
