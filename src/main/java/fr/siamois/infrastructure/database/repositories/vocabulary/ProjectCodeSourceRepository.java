package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.settings.ProjectCodeSource;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectCodeSourceRepository extends CrudRepository<ProjectCodeSource, Long> {

    Optional<ProjectCodeSource> findByActionUnitIdAndFieldCode(Long actionUnitId, String fieldCode);

    List<ProjectCodeSource> findAllByActionUnitId(Long actionUnitId);
}
