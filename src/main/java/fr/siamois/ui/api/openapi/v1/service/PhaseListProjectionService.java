package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.PhaseDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The phase-list pendant of {@link RecordingUnitListProjectionService} — what a page of phases
 * must resolve in a batch on top of the rows themselves: concept labels (type + periods/keywords)
 * and the {@code answers} projection requested via {@code ?fields=}.
 */
@Service
@RequiredArgsConstructor
public class PhaseListProjectionService {

    private final PhaseAnswersProjector phaseAnswersProjector;
    private final ConceptLabelBatchResolver conceptLabelBatchResolver;
    private final AdditionalAnswersListProjector additionalAnswersListProjector;

    public record PhaseListProjection(Map<Long, String> resolvedLabels,
                                       Map<Long, Map<String, Object>> answersByPhaseId) {

        public static PhaseListProjection empty() {
            return new PhaseListProjection(Map.of(), Map.of());
        }

        public Map<String, Object> answersFor(Long phaseId) {
            return answersByPhaseId.get(phaseId);
        }
    }

    public PhaseListProjection build(Collection<PhaseDTO> rows, String fieldsParam, String lang) {
        if (rows == null || rows.isEmpty()) {
            return PhaseListProjection.empty();
        }
        Set<String> fieldIds = phaseAnswersProjector.resolveRequestedFieldIds(fieldsParam);

        List<ConceptDTO> concepts = new ArrayList<>(phaseAnswersProjector.collectConcepts(rows, fieldIds));
        for (PhaseDTO dto : rows) {
            if (dto != null && dto.getType() != null) {
                concepts.add(dto.getType());
            }
        }
        Map<Long, String> labels = conceptLabelBatchResolver.resolveLabels(concepts, lang);

        List<Long> rowIds = rows.stream().filter(java.util.Objects::nonNull).map(PhaseDTO::getId).filter(java.util.Objects::nonNull).toList();
        return new PhaseListProjection(labels, additionalAnswersListProjector.merge(phaseAnswersProjector.project(rows, fieldIds, labels),
                CustomFieldAnswerService.ListOwner.PHASE, rowIds, fieldsParam, fieldIds, lang));
    }

    /**
     * Une seule phase (détail) — même lot de libellés, une seule ligne.
     */
    public PhaseListProjection buildOne(PhaseDTO row, String lang) {
        return build(row == null ? List.of() : List.of(row), PhaseAnswersProjector.FIELDS_ALL, lang);
    }
}
