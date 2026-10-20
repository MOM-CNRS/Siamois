package fr.siamois.infrastructure.database;

import fr.siamois.domain.models.uiview.UiTableView;
import fr.siamois.domain.models.uiview.UiViewKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UiViewRepository extends JpaRepository<UiTableView, Long> {

    /**
     * The user-created views of an organisation. {@code resourceType} is "" for every type and
     * {@code pattern} a lower-case SQL like pattern ("%" for every title): null parameters make
     * PostgreSQL fail to infer their type.
     */
    @Query("select v from UiTableView v where v.institution.id = :institutionId and v.kind = :kind "
            + "and (:resourceType = '' or v.resourceType = :resourceType) "
            + "and lower(v.title) like :pattern")
    Page<UiTableView> search(@Param("institutionId") Long institutionId,
                             @Param("kind") UiViewKind kind,
                             @Param("resourceType") String resourceType,
                             @Param("pattern") String pattern,
                             Pageable pageable);

    /**
     * The default view of a (project, type) pair; 0 stands for "none" in both, as null parameters
     * can't be compared reliably.
     */
    @Query("select v from UiTableView v where v.institution.id = :institutionId and v.kind = :kind "
            + "and v.resourceType = :resourceType "
            + "and coalesce(v.project.id, 0L) = :projectId and coalesce(v.typeId, 0L) = :typeId")
    Optional<UiTableView> findDefault(@Param("institutionId") Long institutionId,
                                      @Param("kind") UiViewKind kind,
                                      @Param("resourceType") String resourceType,
                                      @Param("projectId") Long projectId,
                                      @Param("typeId") Long typeId);

    Optional<UiTableView> findByIdAndInstitutionId(Long id, Long institutionId);
}
