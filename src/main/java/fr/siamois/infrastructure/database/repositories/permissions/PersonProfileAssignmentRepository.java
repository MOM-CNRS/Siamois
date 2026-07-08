package fr.siamois.infrastructure.database.repositories.permissions;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface PersonProfileAssignmentRepository extends CrudRepository<PersonProfileAssignment, PersonProfileAssignment.PersonProfileAssignmentId> {

    @Query("""
            SELECT COUNT(a) > 0
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            JOIN prof.permissions perm
            WHERE a.person.id = :personId
              AND prof.scope = fr.siamois.domain.models.permissions.PermissionScopeType.INSTANCE
              AND perm.code = :permissionCode
            """)
    boolean personHasInstancePermission(@Param("personId") Long personId,
                                        @Param("permissionCode") String permissionCode);

    @Query("""
            SELECT COUNT(a) > 0
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            JOIN prof.permissions perm
            WHERE a.person.id = :personId
              AND prof.scope = fr.siamois.domain.models.permissions.PermissionScopeType.ORGANISATION
              AND prof.institution.id = :institutionId
              AND perm.code = :permissionCode
            """)
    boolean personHasPermissionInInstitution(@Param("personId") Long personId,
                                             @Param("institutionId") Long institutionId,
                                             @Param("permissionCode") String permissionCode);

    @Query("""
            SELECT COUNT(a) > 0
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            JOIN prof.permissions perm
            WHERE a.person.id = :personId
              AND prof.scope = fr.siamois.domain.models.permissions.PermissionScopeType.PROJECT
              AND prof.actionUnit.id = :actionUnitId
              AND perm.code = :permissionCode
            """)
    boolean personHasPermissionInActionUnit(@Param("personId") Long personId,
                                            @Param("actionUnitId") Long actionUnitId,
                                            @Param("permissionCode") String permissionCode);

    @Query("""
            SELECT COUNT(a) > 0
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            WHERE a.person.id = :personId
              AND prof.scope = fr.siamois.domain.models.permissions.PermissionScopeType.PROJECT
              AND prof.actionUnit.id = :actionUnitId
            """)
    boolean personHasAnyProfileOnActionUnit(@Param("personId") Long personId,
                                            @Param("actionUnitId") Long actionUnitId);

    @Query("""
            SELECT COUNT(p) > 0
            FROM PersonProfileAssignment ppa
            JOIN ppa.person p
            JOIN ppa.profile prof
            WHERE prof.code = fr.siamois.domain.models.permissions.ProfileConstants.SUPERADMIN AND p = :admin
            """)
    boolean personIsSuperAdmin(Person admin);

    @Query("""
            SELECT COUNT(a) > 0
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            WHERE a.person.id = :personId
              AND prof.code = fr.siamois.domain.models.permissions.ProfileConstants.SUPERADMIN
            """)
    boolean personIsSuperAdmin(@Param("personId") Long personId);

    @Query("""
            SELECT COUNT(a) > 0
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            WHERE a.person.id = :personId
              AND prof.institution.id = :institutionId
            """)
    boolean personHasAnyProfileInInstitution(@Param("personId") Long personId,
                                             @Param("institutionId") Long institutionId);

    @Query("""
            SELECT COUNT(a) > 0
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            WHERE a.person.id = :personId
              AND prof.institution.id = :institutionId
              AND prof.code = :profileCode
            """)
    boolean personHasProfileWithCodeInInstitution(@Param("personId") Long personId,
                                                  @Param("institutionId") Long institutionId,
                                                  @Param("profileCode") String profileCode);

    @Query("""
            SELECT DISTINCT p
            FROM PersonProfileAssignment a
            JOIN a.person p
            JOIN a.profile prof
            WHERE prof.institution.id = :institutionId
              AND prof.code = :profileCode
            """)
    Set<Person> findAllPersonsByProfileCodeAndInstitutionId(@Param("profileCode") String profileCode,
                                                            @Param("institutionId") Long institutionId);

    @Query("""
            SELECT DISTINCT p
            FROM PersonProfileAssignment a
            JOIN a.person p
            JOIN a.profile prof
            WHERE prof.actionUnit.id = :actionUnitId
            """)
    Set<Person> findAllPersonsByProfileActionUnitId(@Param("actionUnitId") Long actionUnitId);

    void deleteAllByProfileActionUnitId(Long actionUnitId);

    Optional<PersonProfileAssignment> findByProfileIdAndPersonId(Long profileId, Long personId);

    /**
     * All assignments (person and profile fetched) in the institution, restricted to persons
     * holding the {@link fr.siamois.domain.models.permissions.ProfileConstants#ORGANIZATION_MEMBER}
     * profile of the institution. One query loads everything needed to build the member list.
     */
    @Query("""
            SELECT ppa FROM PersonProfileAssignment ppa
            JOIN FETCH ppa.person p
            JOIN FETCH ppa.profile prof
            WHERE prof.institution.id = :institutionId AND prof.actionUnit IS NULL
            """)
    List<PersonProfileAssignment> findAllAssignmentsByInstitutionId(@Param("institutionId") Long institutionId);

    /**
     * All assignments (person and profile fetched) on the action unit. One query loads everything
     * needed to build the project member list.
     */
    @Query("""
            SELECT ppa FROM PersonProfileAssignment ppa
            JOIN FETCH ppa.person p
            JOIN FETCH ppa.profile prof
            WHERE prof.actionUnit.id = :actionUnitId
            """)
    List<PersonProfileAssignment> findAllAssignmentsByActionUnitId(@Param("actionUnitId") Long actionUnitId);

    /**
     * All INSTANCE-scoped assignments (person and profile fetched). One query loads everything
     * needed to build the application member list.
     */
    @Query("""
            SELECT ppa FROM PersonProfileAssignment ppa
            JOIN FETCH ppa.person p
            JOIN FETCH ppa.profile prof
            WHERE prof.institution IS NULL AND prof.actionUnit IS NULL
            """)
    List<PersonProfileAssignment> findAllInstanceAssignments();

    @Query("SELECT ppa FROM PersonProfileAssignment ppa " +
            "WHERE ppa.profile.code = :profileCode AND ppa.profile.institution.id = :institutionId AND ppa.person.id = :personId")
    Optional<PersonProfileAssignment> findByProfileCodeAndInstitutionIdAndPersonId(String profileCode, Long institutionId, Long personId);

    @Query("""
            SELECT COUNT(DISTINCT a.person.id)
            FROM PersonProfileAssignment a
            JOIN a.profile prof
            WHERE prof.institution.id = :institutionId
              AND prof.code = :profileCode
            """)
    long countPersonsByProfileCodeAndInstitutionId(@Param("profileCode") String profileCode,
                                                   @Param("institutionId") Long institutionId);

    @Modifying
    @Query("DELETE FROM PersonProfileAssignment ppa WHERE ppa.profile.institution.id = :institutionId AND ppa.person.id = :personid")
    void deleteByInstitutionIdAndPersonId(Long institutionId, Long personid);
}
