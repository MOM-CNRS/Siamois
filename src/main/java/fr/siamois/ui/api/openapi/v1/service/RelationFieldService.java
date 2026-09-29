package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.vocabulary.ConceptLabelBatchResolver;
import fr.siamois.dto.entity.vocabulary.ConceptDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.infrastructure.database.repositories.relation.RelationFieldRepository;
import fr.siamois.infrastructure.database.repositories.relation.RelationFieldRepository.Row;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link RelationField}'s values in their API shape: {@link ResourceRef}s, each stratigraphic one
 * qualified with what the relationship says ({@link ResourceRef.StratigraphicQualifier}).
 */
@Service
@RequiredArgsConstructor
public class RelationFieldService {

    private final RelationFieldRepository relationFieldRepository;
    private final ConceptLabelBatchResolver conceptLabelBatchResolver;

    /** A page of one owner's values, and how many there are in all. */
    public record ValuesPage(List<ResourceRef> values, long total) {
    }

    /**
     * Each owner's preview: its first {@code limit} values and its total — every owner of
     * {@code ownerIds} gets one, empty when it has no value.
     *
     * @param valuesHref the URL of an owner's full list, for a preview that isn't complete
     */
    @Transactional(readOnly = true)
    public Map<Long, MultiValue> previews(RelationField relation, Collection<Long> ownerIds, int limit, String lang,
                                          Function<Long, String> valuesHref) {
        Map<Long, RelationFieldRepository.Preview> previews = relationFieldRepository.previews(relation, ownerIds, limit);
        Map<Long, String> conceptLabels = conceptLabels(previews.values().stream()
                .flatMap(p -> p.rows().stream()).toList(), lang);
        Map<Long, MultiValue> out = new HashMap<>();
        for (Long ownerId : ownerIds) {
            RelationFieldRepository.Preview preview = previews.get(ownerId);
            if (preview == null) {
                out.put(ownerId, MultiValue.complete(List.of()));
                continue;
            }
            List<ResourceRef> refs = preview.rows().stream().map(row -> toRef(relation, row, conceptLabels)).toList();
            out.put(ownerId, MultiValue.of(refs, preview.total(), limit, valuesHref.apply(ownerId)));
        }
        return out;
    }

    /** One owner's values from {@code offset}, at most {@code limit}, those whose label contains {@code search}. */
    @Transactional(readOnly = true)
    public ValuesPage page(RelationField relation, long ownerId, int offset, int limit,
                           @Nullable String search, boolean ascending, String lang) {
        List<Row> rows = relationFieldRepository.page(relation, ownerId, offset, limit, search, ascending);
        long total = relationFieldRepository.count(relation, ownerId, search);
        Map<Long, String> conceptLabels = conceptLabels(rows, lang);
        return new ValuesPage(rows.stream().map(row -> toRef(relation, row, conceptLabels)).toList(), total);
    }

    private Map<Long, String> conceptLabels(List<Row> rows, String lang) {
        List<ConceptDTO> concepts = rows.stream()
                .filter(row -> row.conceptId() != null)
                .map(row -> ConceptDTO.builder().id(row.conceptId()).externalId(row.conceptExternalId()).build())
                .toList();
        return concepts.isEmpty() ? Map.of() : conceptLabelBatchResolver.resolveLabels(concepts, lang);
    }

    private static ResourceRef toRef(RelationField relation, Row row, Map<Long, String> conceptLabels) {
        String id = String.valueOf(row.targetId());
        if (!relation.qualified()) {
            return new ResourceRef(id, relation.resourceType(), row.label());
        }
        ResourceRef concept = row.conceptId() == null ? null : new ResourceRef(
                String.valueOf(row.conceptId()), FieldAnswerWireService.CONCEPTS,
                Objects.requireNonNullElseGet(conceptLabels.get(row.conceptId()), () -> "[" + row.conceptExternalId() + "]"));
        return new ResourceRef(id, relation.resourceType(), row.label(), new ResourceRef.StratigraphicQualifier(
                concept, row.role(), positionOf(row), row.conceptDirection(), row.asynchronous(), row.uncertain()));
    }

    /**
     * Where the other unit stands relative to the owner — the rule the fiche has always sorted a
     * unit's relationships by ({@code FormService#handleStratigraphyRelationships}): a synchronous
     * relationship is synchronous from both sides; an asynchronous one lists its second unit among
     * its first unit's posterior relationships, and the other way round.
     */
    private static String positionOf(Row row) {
        if (Boolean.FALSE.equals(row.asynchronous())) return "synchronous";
        return "unit1".equals(row.role()) ? "posterior" : "anterior";
    }
}
