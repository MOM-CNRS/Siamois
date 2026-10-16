package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.dto.entity.RecordingUnitDTO;
import fr.siamois.infrastructure.database.repositories.relation.RelationField;
import fr.siamois.ui.api.openapi.v1.resource.form.MultiValue;
import fr.siamois.ui.api.openapi.v1.resource.form.ResourceRef;
import fr.siamois.ui.api.openapi.v1.resource.recordingunit.RecordingUnitRelationGraphResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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

    /**
     * How many stratigraphic relationships are read to be split into their three zones. A unit with
     * more has its zone totals counted on these only: far beyond what a graph can draw.
     */
    private static final int STRATIGRAPHY_READ_LIMIT = 1000;

    private static final String ANTERIOR = "anterior";
    private static final String POSTERIOR = "posterior";
    private static final String SYNCHRONOUS = "synchronous";

    private final RelationFieldService relationFieldService;

    @Transactional(readOnly = true)
    public RecordingUnitRelationGraphResource graphOf(RecordingUnitDTO unit, String lang) {
        long id = unit.getId();
        ResourceRef center = new ResourceRef(String.valueOf(id), "recording-units", unit.getFullIdentifier());

        List<ResourceRef> strati = relationFieldService.page(
                RelationField.RECORDING_UNIT_STRATIGRAPHY, id, 0, STRATIGRAPHY_READ_LIMIT, null, true, lang).values();

        return new RecordingUnitRelationGraphResource(
                center,
                hierarchy(RelationField.RECORDING_UNIT_PARENTS, id, lang),
                hierarchy(RelationField.RECORDING_UNIT_CHILDREN, id, lang),
                zone(strati, ANTERIOR),
                zone(strati, SYNCHRONOUS),
                zone(strati, POSTERIOR),
                MultiValue.complete(List.of()));
    }

    private MultiValue hierarchy(RelationField relation, long id, String lang) {
        RelationFieldService.ValuesPage page = relationFieldService.page(relation, id, 0, ZONE_LIMIT, null, true, lang);
        return MultiValue.of(page.values(), page.total(), ZONE_LIMIT, null);
    }

    /** The stratigraphic references standing at {@code position} relative to the unit. */
    private static MultiValue zone(List<ResourceRef> strati, String position) {
        List<ResourceRef> inZone = strati.stream()
                .filter(ref -> ref.qualifier() != null && Objects.equals(position, ref.qualifier().position()))
                .toList();
        return MultiValue.of(inZone, inZone.size(), ZONE_LIMIT, null);
    }
}
