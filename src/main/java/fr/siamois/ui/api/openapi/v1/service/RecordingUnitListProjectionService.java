package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recording-unit-list pendant of {@link ProjectListProjectionService} — what a page of UEs
 * must resolve in a batch, on top of the rows themselves: concept labels and the {@code answers}
 * projection requested via {@code ?fields=}.
 */
@Service
@RequiredArgsConstructor
public class RecordingUnitListProjectionService {

    private final RecordingUnitAnswersProjector recordingUnitAnswersProjector;
    private final ConceptLabelBatchResolver conceptLabelBatchResolver;

    public record RecordingUnitListProjection(Map<Long, String> resolvedLabels,
                                               Map<Long, Map<String, Object>> answersByRecordingUnitId) {

        public static RecordingUnitListProjection empty() {
            return new RecordingUnitListProjection(Map.of(), Map.of());
        }

        public Map<String, Object> answersFor(Long recordingUnitId) {
            return answersByRecordingUnitId.get(recordingUnitId);
        }
    }

    /**
     * @param fieldsParam {@code all}, {@code default}, a comma-separated list of field ids, or
     *                    {@code null}/blank to project nothing — in which case rows carry no
     *                    {@code answers} key, at no extra cost over the plain page.
     */
    public RecordingUnitListProjection build(Collection<RecordingUnitDTO> rows, String fieldsParam, String lang) {
        if (rows == null || rows.isEmpty()) {
            return RecordingUnitListProjection.empty();
        }
        Set<String> fieldIds = recordingUnitAnswersProjector.resolveRequestedFieldIds(fieldsParam);

        // One batch of concept labels for the whole page: every field's own referenced concepts,
        // plus each row's own type (which the mapper otherwise resolves one row at a time).
        List<ConceptDTO> concepts = new ArrayList<>(recordingUnitAnswersProjector.collectConcepts(rows, fieldIds));
        for (RecordingUnitDTO dto : rows) {
            if (dto != null && dto.getType() != null) {
                concepts.add(dto.getType());
            }
        }
        Map<Long, String> labels = conceptLabelBatchResolver.resolveLabels(concepts, lang);

        return new RecordingUnitListProjection(labels, recordingUnitAnswersProjector.project(rows, fieldIds, labels));
    }
}
