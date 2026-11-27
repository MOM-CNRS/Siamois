package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.domain.services.relation.RelationFieldIndex;
import fr.siamois.infrastructure.database.repositories.relation.DefinitionRelation;
import fr.siamois.infrastructure.database.repositories.relation.RelationSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which relation a field of an owner type reads: a system relation field of the current model, else the side of a
 * configurable relation the field is ({@link DefinitionRelation}). The first is known from the code, the second
 * from {@link RelationFieldIndex}, so a page asks the database nothing more to know it.
 */
@Service
@RequiredArgsConstructor
public class RelationSourceResolver {

    private final RelationFieldIndex fieldIndex;

    public Optional<RelationSource> of(Class<?> ownerType, String fieldId) {
        Optional<RelationSource> system = MultiValueAnswers.relationFieldOf(ownerType, fieldId).map(f -> f);
        return system.isPresent() ? system : Optional.ofNullable(configurable(ownerType, List.of(fieldId)).get(fieldId));
    }

    /** The configurable relation fields among {@code fieldIds}, by field id; the others are left out. */
    public Map<String, RelationSource> configurable(Class<?> ownerType, Collection<String> fieldIds) {
        Map<String, RelationSource> out = new LinkedHashMap<>();
        Map<Long, RelationFieldIndex.Side> sides = null;
        for (String fieldId : fieldIds) {
            // a system relation field, or a computed column: no configurable relation behind it
            Long id = MultiValueAnswers.relationFieldOf(ownerType, fieldId).isPresent() ? null : parse(fieldId);
            if (id == null) continue;
            if (sides == null) sides = fieldIndex.sides();
            RelationFieldIndex.Side side = sides.get(id);
            if (side != null) {
                out.put(fieldId, new DefinitionRelation(side.definitionId(), side.side(), side.symmetric(),
                        side.complex(), DefinitionRelation.kindOfOwner(ownerType)));
            }
        }
        return out;
    }

    private static Long parse(String fieldId) {
        try {
            return Long.valueOf(fieldId);
        } catch (NumberFormatException notAFieldId) {
            return null; // a computed column: no relation behind it
        }
    }
}
