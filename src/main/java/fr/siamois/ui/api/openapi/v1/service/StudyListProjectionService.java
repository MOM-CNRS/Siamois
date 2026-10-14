package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.study.Study;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.StudyDTO;
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
 * The study-list pendant of {@link PhaseListProjectionService} — what a page of studies
 * must resolve in a batch on top of the rows themselves: concept labels (type) and the
 * {@code answers} projection requested via {@code ?fields=}.
 */
@Service
@RequiredArgsConstructor
public class StudyListProjectionService {

    private final StudyAnswersProjector studyAnswersProjector;
    private final ConceptLabelBatchResolver conceptLabelBatchResolver;
    private final MultiValueAnswers multiValueAnswers;
    private final AdditionalAnswersListProjector additionalAnswersListProjector;

    public record StudyListProjection(Map<Long, String> resolvedLabels,
                                           Map<Long, Map<String, Object>> answersByStudyId) {

        public static StudyListProjection empty() {
            return new StudyListProjection(Map.of(), Map.of());
        }

        public Map<String, Object> answersFor(Long studyId) {
            return answersByStudyId.get(studyId);
        }
    }

    public StudyListProjection build(Collection<StudyDTO> rows, String fieldsParam, String lang) {
        return build(rows, fieldsParam, lang, ValuesLimit.forList());
    }

    /** {@link #build(Collection, String, String)} with each multi-valued answer cut to {@code valuesLimit} values. */
    public StudyListProjection build(Collection<StudyDTO> rows, String fieldsParam, String lang, int valuesLimit) {
        if (rows == null || rows.isEmpty()) {
            return StudyListProjection.empty();
        }
        Set<String> fieldIds = studyAnswersProjector.resolveRequestedFieldIds(fieldsParam);

        List<ConceptDTO> concepts = new ArrayList<>(studyAnswersProjector.collectConcepts(rows, fieldIds));
        for (StudyDTO dto : rows) {
            if (dto != null && dto.getType() != null) {
                concepts.add(dto.getType());
            }
        }
        Map<Long, String> labels = conceptLabelBatchResolver.resolveLabels(concepts, lang);

        List<Long> rowIds = rows.stream().filter(java.util.Objects::nonNull).map(StudyDTO::getId).filter(java.util.Objects::nonNull).toList();
        Map<Long, Map<String, Object>> answers = additionalAnswersListProjector.merge(studyAnswersProjector.project(rows, fieldIds, labels),
                CustomFieldAnswerService.ListOwner.STUDY, rowIds, fieldsParam, fieldIds, lang);
        return new StudyListProjection(labels, multiValueAnswers.shape(Study.class, answers, fieldIds, valuesLimit, lang));
    }

    /** Une seule étude (détail) — les valeurs multiples coupées à la limite d'un détail. */
    public StudyListProjection buildOne(StudyDTO row, String lang) {
        return build(row == null ? List.of() : List.of(row), StudyAnswersProjector.FIELDS_ALL, lang, ValuesLimit.forDetail());
    }
}
