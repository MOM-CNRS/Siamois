package fr.siamois.infrastructure.database.repositories.document;

import fr.siamois.domain.models.document.DocumentImportItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentImportItemRepository extends JpaRepository<DocumentImportItem, Long> {

    Optional<DocumentImportItem> findByIdAndDraftId(Long id, Long draftId);

    long countByDraftId(Long draftId);
}
