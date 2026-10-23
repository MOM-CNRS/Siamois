package fr.siamois.infrastructure.database.repositories.vocabulary;

import fr.siamois.domain.models.settings.DocumentationThesaurus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentationThesaurusRepository extends CrudRepository<DocumentationThesaurus, Long> {

    @Query("SELECT dt FROM DocumentationThesaurus dt WHERE dt.institution.id = :institutionId AND dt.actionUnit IS NULL")
    Optional<DocumentationThesaurus> findOfInstitution(@Param("institutionId") Long institutionId);

    Optional<DocumentationThesaurus> findByActionUnitId(Long actionUnitId);

    List<DocumentationThesaurus> findAllByActionUnitId(Long actionUnitId);
}
