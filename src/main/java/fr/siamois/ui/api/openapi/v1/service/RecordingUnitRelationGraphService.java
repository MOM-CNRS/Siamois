package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.models.settings.tableconfig.ConfigurableTable;
import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitRelationGraphResource;
import fr.siamois.ui.table.definitions.SystemFieldCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the relations of a recording unit as the zones of its graph. Everything goes through
 * {@link RelationFieldService}, so when a relation moves to another storage only its
 * {@link RelationField} changes, not this grouping nor the resource it returns.
 */
@Service
@RequiredArgsConstructor
public class RecordingUnitRelationGraphService {

    /** The most units each zone carries; the zone's total says how many there are in all. */
    public static final int ZONE_LIMIT = 30;
    /** The most units each zone carries when the graph is read whole, with the links between its units. */
    public static final int FULL_ZONE_LIMIT = 300;

    private final RelationFieldService relationFieldService;

    @Transactional(readOnly = true)
    public RecordingUnitRelationGraphResource graphOf(RecordingUnitDTO unit, String lang) {
        return readGraph(unit, lang, false);
    }

    /**
     * @param full each zone carries up to {@link #FULL_ZONE_LIMIT} units rather than {@link #ZONE_LIMIT}, and the graph
     *             lists the relations between its units
     */
    @Transactional(readOnly = true)
    public RecordingUnitRelationGraphResource graphOf(RecordingUnitDTO unit, String lang, boolean full) {
        return readGraph(unit, lang, full);
    }

    private RecordingUnitRelationGraphResource readGraph(RecordingUnitDTO unit, String lang, boolean full) {
        long id = unit.getId();
        int limit = full ? FULL_ZONE_LIMIT : ZONE_LIMIT;
        MultiValue parents = zone(RelationField.RECORDING_UNIT_PARENTS, id, limit, lang);
        MultiValue children = zone(RelationField.RECORDING_UNIT_CHILDREN, id, limit, lang);
        MultiValue anterior = zone(RelationField.RECORDING_UNIT_ANTERIOR, id, limit, lang);
        MultiValue synchronous = zone(RelationField.RECORDING_UNIT_SYNCHRONOUS, id, limit, lang);
        MultiValue posterior = zone(RelationField.RECORDING_UNIT_POSTERIOR, id, limit, lang);
        ResourceRef center = new ResourceRef(String.valueOf(id), "recording-units", unit.getFullIdentifier());

        return new RecordingUnitRelationGraphResource(
                center,
                parents, children, anterior, synchronous, posterior,
                MultiValue.complete(List.of()),
                new RecordingUnitRelationGraphResource.Fields(
                        fieldIdOf(RelationField.RECORDING_UNIT_ANTERIOR),
                        fieldIdOf(RelationField.RECORDING_UNIT_SYNCHRONOUS),
                        fieldIdOf(RelationField.RECORDING_UNIT_POSTERIOR)),
                full ? linksBetweenNeighbours(id, List.of(parents, children, anterior, synchronous, posterior), lang) : List.of());
    }

    /** The relations that join two of the units a graph shows, not counting the center's own: the graph draws those. */
    private List<RecordingUnitRelationGraphResource.Link> linksBetweenNeighbours(long centerId, List<MultiValue> zones, String lang) {
        Set<Long> shown = new LinkedHashSet<>();
        zones.forEach(zone -> zone.values().forEach(ref -> shown.add(Long.parseLong(ref.resourceId()))));
        shown.add(centerId);
        return relationFieldService.linksAmong(shown, lang).stream()
                .filter(link -> link.fromId() != centerId && link.toId() != centerId)
                .map(link -> new RecordingUnitRelationGraphResource.Link(
                        String.valueOf(link.fromId()), String.valueOf(link.toId()), link.kind(), link.type(), link.uncertain()))
                .distinct()
                .toList();
    }

    private static String fieldIdOf(RelationField relation) {
        return String.valueOf(SystemFieldCatalog.fieldBoundTo(ConfigurableTable.UE, relation.valueBinding()).getId());
    }

    /**
     * Every relation of a unit as a flat, paged list, each saying which kind of relation it is: the
     * graph's zones without their bound, for the list view of the same relations.
     *
     * @param kinds    the kinds to keep; empty keeps them all
     * @param search   keeps the units whose identifier contains this text
     * @param byLabel  sorted by the other unit's identifier rather than by kind
     */
    @Transactional(readOnly = true)
    public RelationFieldService.KindedPage relationsOf(RecordingUnitDTO unit, Collection<RecordingUnitRelationKind> kinds,
                                                       @Nullable String search, boolean byLabel, boolean ascending,
                                                       int offset, int limit, String lang) {
        Map<String, RelationField> fields = new LinkedHashMap<>();
        for (RecordingUnitRelationKind kind : RecordingUnitRelationKind.values()) {
            if (kinds.isEmpty() || kinds.contains(kind)) fields.put(kind.wireName(), kind.field());
        }
        return relationFieldService.pageAcross(fields, unit.getId(), offset, limit, search, byLabel, ascending, lang);
    }

    private MultiValue zone(RelationField relation, long id, int limit, String lang) {
        RelationFieldService.ValuesPage page = relationFieldService.page(relation, id, 0, limit, null, true, lang);
        return MultiValue.of(page.values(), page.total(), limit, null);
    }
}
