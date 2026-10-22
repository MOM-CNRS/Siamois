package fr.siamois.infrastructure.database.repositories.document;

import fr.siamois.domain.models.document.DocumentImportDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentImportDraftRepository extends JpaRepository<DocumentImportDraft, Long> {

    Optional<DocumentImportDraft> findByPersonIdAndInstitutionId(Long personId, Long institutionId);
}
