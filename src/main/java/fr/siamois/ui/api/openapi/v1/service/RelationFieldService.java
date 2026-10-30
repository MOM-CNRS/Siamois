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

import java.util.*;
import java.util.function.LongFunction;

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

    /** A value of one of several relations, and the name of the relation it comes from. */
    public record KindedValue(String kind, ResourceRef value) {
    }

    /** A page of one owner's values across several relations, and how many there are in all. */
    public record KindedPage(List<KindedValue> values, long total) {
    }

    /**
     * Each owner's preview: its first {@code limit} values and its total — every owner of
     * {@code ownerIds} gets one, empty when it has no value.
     *
     * @param valuesHref the URL of an owner's full list, for a preview that isn't complete
     */
    @Transactional(readOnly = true)
    public Map<Long, MultiValue> previews(RelationField relation, Collection<Long> ownerIds, int limit, String lang,
                                          LongFunction<String> valuesHref) {
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

    /**
     * One owner's values across several relations at once, each with the name of its relation, from
     * {@code offset}, at most {@code limit}, those whose label contains {@code search}.
     *
     * @param kinds   the relations to read, by name; their order is the order of the {@code kind} sort
     * @param byLabel sorted by label (then relation) rather than by relation (then label)
     */
    @Transactional(readOnly = true)
    public KindedPage pageAcross(Map<String, RelationField> kinds, long ownerId, int offset, int limit,
                                 @Nullable String search, boolean byLabel, boolean ascending, String lang) {
        List<RelationFieldRepository.KindedRow> rows =
                relationFieldRepository.pageAcross(kinds, ownerId, offset, limit, search, byLabel, ascending);
        long total = relationFieldRepository.countAcross(kinds, ownerId, search);
        Map<Long, String> conceptLabels = conceptLabels(rows.stream().map(RelationFieldRepository.KindedRow::row).toList(), lang);
        return new KindedPage(rows.stream()
                .map(row -> new KindedValue(row.kind(), toRef(kinds.get(row.kind()), row.row(), conceptLabels)))
                .toList(), total);
    }

    /** A relation between two units of a set: see {@link RelationFieldRepository.LinkRow}; its type is labelled. */
    public record Link(String kind, long fromId, long toId, @Nullable ResourceRef type, @Nullable Boolean uncertain) {
    }

    /** The relations that join two of {@code unitIds}, each with its type labelled in {@code lang}. */
    @Transactional(readOnly = true)
    public List<Link> linksAmong(Collection<Long> unitIds, String lang) {
        List<RelationFieldRepository.LinkRow> rows = relationFieldRepository.linksAmong(unitIds);
        Map<Long, String> labels = resolveConceptLabels(rows.stream()
                .filter(row -> row.conceptId() != null)
                .map(row -> conceptOf(row.conceptId(), row.conceptExternalId()))
                .toList(), lang);
        return rows.stream().map(row -> new Link(row.kind(), row.fromId(), row.toId(), row.conceptId() == null ? null : conceptRef(
                row.conceptId(), row.conceptExternalId(), labels), row.uncertain())).toList();
    }

    private Map<Long, String> conceptLabels(List<Row> rows, String lang) {
        return resolveConceptLabels(rows.stream()
                .filter(row -> row.conceptId() != null)
                .map(row -> conceptOf(row.conceptId(), row.conceptExternalId()))
                .toList(), lang);
    }

    private static ConceptDTO conceptOf(long conceptId, @Nullable String externalId) {
        return ConceptDTO.builder().id(conceptId).externalId(externalId).build();
    }

    private Map<Long, String> resolveConceptLabels(List<ConceptDTO> concepts, String lang) {
        return concepts.isEmpty() ? Map.of() : conceptLabelBatchResolver.resolveLabels(concepts, lang);
    }

    /** A concept's reference, labelled by its resolved label, or by its external id in brackets when it has none. */
    private static ResourceRef conceptRef(long conceptId, @Nullable String externalId, Map<Long, String> labels) {
        return new ResourceRef(String.valueOf(conceptId), FieldAnswerWireService.CONCEPTS,
                Objects.requireNonNullElseGet(labels.get(conceptId), () -> "[" + externalId + "]"));
    }

    private static ResourceRef toRef(RelationField relation, Row row, Map<Long, String> conceptLabels) {
        String id = String.valueOf(row.targetId());
        if (!relation.qualified()) {
            return new ResourceRef(id, relation.resourceType(), row.label());
        }
        ResourceRef concept = row.conceptId() == null || !typeReadsFromOwner(row) ? null
                : conceptRef(row.conceptId(), row.conceptExternalId(), conceptLabels);
        return new ResourceRef(id, relation.resourceType(), row.label(), new ResourceRef.StratigraphicQualifier(
                concept, row.role(), positionOf(row), row.conceptDirection(), row.asynchronous(), row.uncertain()));
    }

    /**
     * Whether the relationship's type makes sense read from the owner. It reads from one unit toward
     * the other ({@code concept_direction}: true from the first unit): "008 recouvre 006" says nothing
     * of 006, whose side only has the number of the other unit. A synchronous relationship reads the
     * same from both sides, and a type with no recorded direction is shown as it is.
     */
    private static boolean typeReadsFromOwner(Row row) {
        if ("synchronous".equals(positionOf(row)) || row.conceptDirection() == null) return true;
        return row.conceptDirection() == "unit1".equals(row.role());
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
