package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The container-list pendant of {@link PhaseListProjectionService} — what a page of containers
 * must resolve in a batch on top of the rows themselves: concept labels (type) and the
 * {@code answers} projection requested via {@code ?fields=}.
 */
@Service
@RequiredArgsConstructor
public class ContainerListProjectionService {

    private final ContainerAnswersProjector containerAnswersProjector;
    private final ConceptLabelBatchResolver conceptLabelBatchResolver;
    private final AdditionalAnswersListProjector additionalAnswersListProjector;

    public record ContainerListProjection(Map<Long, String> resolvedLabels,
                                           Map<Long, Map<String, Object>> answersByContainerId) {

        public static ContainerListProjection empty() {
            return new ContainerListProjection(Map.of(), Map.of());
        }

        public Map<String, Object> answersFor(Long containerId) {
            return answersByContainerId.get(containerId);
        }
    }

    public ContainerListProjection build(Collection<ContainerDTO> rows, String fieldsParam, String lang) {
        if (rows == null || rows.isEmpty()) {
            return ContainerListProjection.empty();
        }
        Set<String> fieldIds = containerAnswersProjector.resolveRequestedFieldIds(fieldsParam);

        List<ConceptDTO> concepts = new ArrayList<>(containerAnswersProjector.collectConcepts(rows, fieldIds));
        for (ContainerDTO dto : rows) {
            if (dto != null && dto.getType() != null) {
                concepts.add(dto.getType());
            }
        }
        Map<Long, String> labels = conceptLabelBatchResolver.resolveLabels(concepts, lang);

        List<Long> rowIds = rows.stream().filter(java.util.Objects::nonNull).map(ContainerDTO::getId).filter(java.util.Objects::nonNull).toList();
        return new ContainerListProjection(labels, additionalAnswersListProjector.merge(containerAnswersProjector.project(rows, fieldIds, labels),
                CustomFieldAnswerService.ListOwner.CONTAINER, rowIds, fieldsParam, fieldIds, lang));
    }

    public ContainerListProjection buildOne(ContainerDTO row, String lang) {
        return build(row == null ? List.of() : List.of(row), ContainerAnswersProjector.FIELDS_ALL, lang);
    }
}
