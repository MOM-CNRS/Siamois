package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.container.Container;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.ContainerDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
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
    private final MultiValueAnswers multiValueAnswers;
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
        return build(rows, fieldsParam, lang, ValuesLimit.forList());
    }

    /** {@link #build(Collection, String, String)} with each multi-valued answer cut to {@code valuesLimit} values. */
    public ContainerListProjection build(Collection<ContainerDTO> rows, String fieldsParam, String lang, int valuesLimit) {
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
        Map<Long, Map<String, Object>> answers = additionalAnswersListProjector.merge(containerAnswersProjector.project(rows, fieldIds, labels),
                CustomFieldAnswerService.ListOwner.CONTAINER, rowIds, fieldsParam, fieldIds, lang);
        return new ContainerListProjection(labels, multiValueAnswers.shape(Container.class, answers, fieldIds, valuesLimit, lang));
    }

    /** Un seul contenant (détail) — les valeurs multiples coupées à la limite d'un détail. */
    public ContainerListProjection buildOne(ContainerDTO row, String lang) {
        return build(row == null ? List.of() : List.of(row), ContainerAnswersProjector.FIELDS_ALL, lang, ValuesLimit.forDetail());
    }
}
