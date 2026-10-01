package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.specimen.Specimen;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.SpecimenDTO;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.ui.api.openapi.v1.request.list.ValuesLimit;
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
    private final MultiValueAnswers multiValueAnswers;
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
        return build(rows, fieldsParam, lang, ValuesLimit.forList());
    }

    /** {@link #build(Collection, String, String)} with each multi-valued answer cut to {@code valuesLimit} values. */
    public FindListProjection build(Collection<SpecimenDTO> rows, String fieldsParam, String lang, int valuesLimit) {
        if (rows == null || rows.isEmpty() || fieldsParam == null) {
            return FindListProjection.empty();
        }
        Set<String> fieldIds = specimenAnswersProjector.resolveRequestedFieldIds(fieldsParam);
        List<ConceptDTO> concepts = specimenAnswersProjector.collectConcepts(rows, fieldIds);
        Map<Long, String> labels = concepts.isEmpty() ? Map.of() : conceptLabelBatchResolver.resolveLabels(concepts, lang);

        List<Long> rowIds = rows.stream().filter(Objects::nonNull).map(SpecimenDTO::getId).filter(Objects::nonNull).toList();
        Map<Long, Map<String, Object>> answers = additionalAnswersListProjector.merge(specimenAnswersProjector.project(rows, fieldIds, labels),
                CustomFieldAnswerService.ListOwner.SPECIMEN, rowIds, fieldsParam, fieldIds, lang);
        return new FindListProjection(multiValueAnswers.shape(Specimen.class, answers, fieldIds, valuesLimit, lang));
    }
}
