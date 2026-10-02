package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.document.Document;
import fr.siamois.domain.services.form.CustomFieldAnswerService;
import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.DocumentDTO;
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
 * The document-list pendant of {@link RecordingUnitListProjectionService} — what a page of documents
 * must resolve in a batch on top of the rows themselves: concept labels (category, type, support nature, keywords, language)
 * and the {@code answers} projection requested via {@code ?fields=}.
 */
@Service
@RequiredArgsConstructor
public class DocumentListProjectionService {

    private final DocumentAnswersProjector documentAnswersProjector;
    private final ConceptLabelBatchResolver conceptLabelBatchResolver;
    private final MultiValueAnswers multiValueAnswers;
    private final AdditionalAnswersListProjector additionalAnswersListProjector;

    public record DocumentListProjection(Map<Long, String> resolvedLabels,
                                       Map<Long, Map<String, Object>> answersByDocumentId) {

        public static DocumentListProjection empty() {
            return new DocumentListProjection(Map.of(), Map.of());
        }

        public Map<String, Object> answersFor(Long documentId) {
            return answersByDocumentId.get(documentId);
        }
    }

    public DocumentListProjection build(Collection<DocumentDTO> rows, String fieldsParam, String lang) {
        return build(rows, fieldsParam, lang, ValuesLimit.forList());
    }

    /** {@link #build(Collection, String, String)} with each multi-valued answer cut to {@code valuesLimit} values. */
    public DocumentListProjection build(Collection<DocumentDTO> rows, String fieldsParam, String lang, int valuesLimit) {
        if (rows == null || rows.isEmpty()) {
            return DocumentListProjection.empty();
        }
        Set<String> fieldIds = documentAnswersProjector.resolveRequestedFieldIds(fieldsParam);

        List<ConceptDTO> concepts = new ArrayList<>(documentAnswersProjector.collectConcepts(rows, fieldIds));
        for (DocumentDTO dto : rows) {
            if (dto != null && dto.getCategory() != null) {
                concepts.add(dto.getCategory());
            }
        }
        Map<Long, String> labels = conceptLabelBatchResolver.resolveLabels(concepts, lang);

        List<Long> rowIds = rows.stream().filter(java.util.Objects::nonNull).map(DocumentDTO::getId).filter(java.util.Objects::nonNull).toList();
        Map<Long, Map<String, Object>> answers = additionalAnswersListProjector.merge(documentAnswersProjector.project(rows, fieldIds, labels),
                CustomFieldAnswerService.ListOwner.DOCUMENT, rowIds, fieldsParam, fieldIds, lang);
        return new DocumentListProjection(labels, multiValueAnswers.shape(Document.class, answers, fieldIds, valuesLimit, lang));
    }

    /**
     * Un seul document (détail) — même lot de libellés, une seule ligne, les valeurs multiples coupées
     * à la limite d'un détail.
     */
    public DocumentListProjection buildOne(DocumentDTO row, String lang) {
        return build(row == null ? List.of() : List.of(row), DocumentAnswersProjector.FIELDS_ALL, lang, ValuesLimit.forDetail());
    }
}
