package fr.siamois.infrastructure.database.repositories.exporttemplate;

import fr.siamois.domain.models.exporttemplate.ExportTemplate;
import fr.siamois.domain.models.institution.Institution;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExportTemplateRepository extends CrudRepository<ExportTemplate, Long> {

    List<ExportTemplate> findByInstitutionOrderByNameAsc(Institution institution);

    Optional<ExportTemplate> findByIdAndInstitution(Long id, Institution institution);

    boolean existsByInstitutionAndTemplateUuid(Institution institution, String templateUuid);
}
