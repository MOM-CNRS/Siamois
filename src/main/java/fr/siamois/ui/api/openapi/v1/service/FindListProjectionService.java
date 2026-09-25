package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The find-list pendant of {@link ContainerListProjectionService}: the {@code answers} projection
 * requested via {@code ?fields=} for a whole page — system fields from {@link SpecimenAnswersProjector},
 * additional fields merged by {@link AdditionalAnswersListProjector}.
 */
@Service
@RequiredArgsConstructor
public class FindListProjectionService {

    private final SpecimenAnswersProjector specimenAnswersProjector;
    private final ConceptLabelBatchResolver conceptLabelBatchResolver;
    private final AdditionalAnswersListProjector additionalAnswersListProjector;

    public record FindListProjection(Map<Long, Map<String, Object>> answersByFindId) {

        public static FindListProjection empty() {
            return new FindListProjection(Map.of());
        }

        public Map<String, Object> answersFor(Long findId) {
            return answersByFindId.get(findId);
        }
    }

    public FindListProjection build(Collection<SpecimenDTO> rows, String fieldsParam, String lang) {
        if (rows == null || rows.isEmpty() || fieldsParam == null) {
            return FindListProjection.empty();
        }
        Set<String> fieldIds = specimenAnswersProjector.resolveRequestedFieldIds(fieldsParam);
        List<ConceptDTO> concepts = specimenAnswersProjector.collectConcepts(rows, fieldIds);
        Map<Long, String> labels = concepts.isEmpty() ? Map.of() : conceptLabelBatchResolver.resolveLabels(concepts, lang);

        List<Long> rowIds = rows.stream().filter(Objects::nonNull).map(SpecimenDTO::getId).filter(Objects::nonNull).toList();
        return new FindListProjection(additionalAnswersListProjector.merge(specimenAnswersProjector.project(rows, fieldIds, labels),
                CustomFieldAnswerService.ListOwner.SPECIMEN, rowIds, fieldsParam, fieldIds, lang));
    }
}
