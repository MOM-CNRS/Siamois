package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.infrastructure.database.repositories.DocumentRepository;
import fr.siamois.ui.api.openapi.v1.resource.FirstImageCarrier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Sets {@code firstImageDocumentId} on a list page: one query per page (not one per row), the lowest
 * id among the image documents linked to each row. The front shows it as the row's card picture.
 */
@Service
@RequiredArgsConstructor
public class FirstImageService {

    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public void markRecordingUnits(Collection<? extends FirstImageCarrier> rows) {
        mark(rows, documentRepository::findFirstImageIdsOfRecordingUnits);
    }

    @Transactional(readOnly = true)
    public void markFinds(Collection<? extends FirstImageCarrier> rows) {
        mark(rows, documentRepository::findFirstImageIdsOfSpecimens);
    }

    private static void mark(Collection<? extends FirstImageCarrier> rows, Function<Collection<Long>, List<Object[]>> query) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream()
                .map(FirstImageCarrier::getId)
                .filter(Objects::nonNull)
                .map(Long::valueOf)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Long> firstImage = new HashMap<>();
        for (Object[] row : query.apply(ids)) {
            firstImage.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        for (FirstImageCarrier row : rows) {
            Long documentId = row.getId() == null ? null : firstImage.get(Long.valueOf(row.getId()));
            if (documentId != null) {
                row.setFirstImageDocumentId(String.valueOf(documentId));
            }
        }
    }
}
