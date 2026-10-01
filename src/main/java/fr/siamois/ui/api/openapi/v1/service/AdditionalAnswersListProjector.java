package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.form.CustomFieldAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adds the answers to additional fields (those a form configuration adds to a type — not bound
 * to an entity property, so the per-entity answers projectors know nothing of them) to a page's
 * {@code answers} projection, for the field ids a list asked for in {@code ?fields=}.
 *
 * <p>One query for the whole page ({@link CustomFieldAnswerService#loadAdditionalFieldAnswers}),
 * serialized the way the detail serializes them ({@link FieldAnswerWireService#additionalAnswers}).
 * Ids the entity's own projector already covers, and anything that isn't an id ({@code all},
 * {@code default}), are left to that projector.</p>
 */
@Component
@RequiredArgsConstructor
public class AdditionalAnswersListProjector {

    private final CustomFieldAnswerService customFieldAnswerService;
    private final FieldAnswerWireService fieldAnswerWireService;

    /**
     * @param projected the entity projector's own answers per row id (not modified)
     * @param covered   the field ids that projector handled
     * @return {@code projected} plus the additional answers, per row id
     */
    public Map<Long, Map<String, Object>> merge(Map<Long, Map<String, Object>> projected,
                                                CustomFieldAnswerService.ListOwner owner,
                                                Collection<Long> rowIds,
                                                String fieldsParam,
                                                Set<String> covered,
                                                String lang) {
        Set<Long> additionalIds = additionalFieldIds(fieldsParam, covered);
        if (additionalIds.isEmpty() || rowIds.isEmpty()) return projected;

        Map<Long, Map<String, Object>> out = new HashMap<>();
        projected.forEach((id, answers) -> out.put(id, new LinkedHashMap<>(answers)));
        customFieldAnswerService.loadAdditionalFieldAnswers(owner, rowIds, additionalIds)
                .forEach((rowId, answers) -> out.computeIfAbsent(rowId, k -> new LinkedHashMap<>())
                        .putAll(fieldAnswerWireService.additionalAnswers(answers, lang)));
        // A row with none of these answers still gets an answers map when one was asked for.
        for (Long rowId : rowIds) out.computeIfAbsent(rowId, k -> new LinkedHashMap<>());
        return out;
    }

    private static Set<Long> additionalFieldIds(String fieldsParam, Set<String> covered) {
        if (fieldsParam == null || fieldsParam.isBlank()) return Set.of();
        Set<Long> ids = new LinkedHashSet<>();
        for (String raw : fieldsParam.split(",")) {
            String id = raw.trim();
            if (id.isEmpty() || (covered != null && covered.contains(id))) continue;
            try {
                ids.add(Long.parseLong(id));
            } catch (NumberFormatException notAnId) {
                // "all"/"default" and unknown tokens: the projector's business.
            }
        }
        return ids;
    }
}
