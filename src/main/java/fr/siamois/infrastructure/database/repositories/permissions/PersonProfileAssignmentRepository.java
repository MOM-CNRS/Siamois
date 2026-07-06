package fr.siamois.infrastructure.database.repositories.permissions;

import fr.siamois.domain.models.permissions.PersonProfileAssignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
