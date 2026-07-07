package fr.siamois.infrastructure.database.repositories.permissions;

import fr.siamois.domain.models.auth.Person;
import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import fr.siamois.domain.models.permissions.Profile;
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

    @Query("SELECT ppa.profile FROM PersonProfileAssignment ppa " +
            "WHERE ppa.person.id = :personId AND ppa.profile.institution.id = :institutionId")
    List<Profile> findAllProfilesOfPersonInInstitution(Long personId, Long institutionId);

    @Query("SELECT ppa.profile FROM PersonProfileAssignment ppa " +
            "WHERE ppa.person.id = :personId AND ppa.profile.actionUnit.id = :actionUnitId")
    List<Profile> findAllProfilesOfPersonInActionUnit(Long personId, Long actionUnitId);
}
