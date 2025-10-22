package fr.siamois.infrastructure.database.repositories.specimen;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.specimen.SpecimenStudy;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecimenStudyRepository extends CrudRepository<SpecimenStudy, Long>, RevisionRepository<SpecimenStudy, Long, Long> {

    List<SpecimenStudy> findAllByArkIsNullAndCreatedByInstitution(@NotNull Institution createdByInstitution);
}

