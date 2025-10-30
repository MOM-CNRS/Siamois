package fr.siamois.infrastructure.database.repositories.recordingunit;

import fr.siamois.domain.models.institution.Institution;
import fr.siamois.domain.models.recordingunit.RecordingUnitStudy;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecordingUnitStudyRepository extends CrudRepository<RecordingUnitStudy, Long>, RevisionRepository<RecordingUnitStudy, Long, Long> {

    List<RecordingUnitStudy> findAllByArkIsNullAndCreatedByInstitution(@NotNull Institution createdByInstitution);
}

