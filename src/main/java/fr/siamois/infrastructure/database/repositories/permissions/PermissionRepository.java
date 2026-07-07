package fr.siamois.infrastructure.database.repositories.permissions;

import fr.siamois.domain.models.permissions.Permission;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionRepository extends CrudRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
}
