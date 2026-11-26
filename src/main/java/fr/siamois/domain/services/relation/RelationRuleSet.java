package fr.siamois.domain.services.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationRule;
import fr.siamois.domain.models.relation.RelationSide;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The rules a link of a definition obeys, read whole from one scope (docs/modele-relations.md, §3.3).
 *
 * @param rule                  the stored rule set they come from, or null when the definition has none in any scope
 *                              (then nothing is restricted)
 * @param kinds                 the kinds each side accepts; none on a side: any record
 * @param mirrorByConcept       qualifier concept id → its mirror's id; a concept with no pair is its own mirror
 * @param excludedDefinitionIds the definitions that cannot link the same pair
 */
public record RelationRuleSet(RelationDefinition definition,
                              @Nullable RelationRule rule,
                              boolean noCycle,
                              boolean sourceUnique,
                              boolean targetUnique,
                              List<AcceptedKind> kinds,
                              Map<Long, Long> mirrorByConcept,
                              List<Long> excludedDefinitionIds) {

    /** A kind one side accepts, narrowed to a type concept or not. */
    public record AcceptedKind(RelationSide side, RelationEndKind kind, @Nullable Long subtypeId) {
    }

    public RelationRuleSet {
        kinds = List.copyOf(kinds);
        mirrorByConcept = Map.copyOf(mirrorByConcept);
        excludedDefinitionIds = List.copyOf(excludedDefinitionIds);
    }

    /** No restriction at all: a definition with no rule set in any scope. */
    public static RelationRuleSet none(RelationDefinition definition) {
        return new RelationRuleSet(definition, null, false, false, false, List.of(), Map.of(), List.of());
    }

    /** Cycles are refused when the rule set says so, and always for a hierarchical relation. */
    public boolean forbidsCycles() {
        return noCycle || definition.isHierarchical();
    }

    /** Whether the writes of this definition need the project lock: a check reads other links. */
    public boolean needsLock() {
        return forbidsCycles() || sourceUnique || targetUnique || !excludedDefinitionIds.isEmpty();
    }

    /** Whether one side accepts the record's kind at all, whatever its type. */
    public boolean acceptsKind(RelationSide side, RelationEndKind kind) {
        List<AcceptedKind> ofSide = kindsOf(side);
        return ofSide.isEmpty() || ofSide.stream().anyMatch(k -> k.kind() == kind);
    }

    /** Whether one side accepts the record: its kind, and its type when the accepted kind is narrowed to one. */
    public boolean accepts(RelationSide side, RelationEndKind kind, @Nullable Long typeId) {
        List<AcceptedKind> ofSide = kindsOf(side);
        return ofSide.isEmpty() || ofSide.stream().anyMatch(k -> k.kind() == kind
                && (k.subtypeId() == null || Objects.equals(k.subtypeId(), typeId)));
    }

    public List<AcceptedKind> kindsOf(RelationSide side) {
        return kinds.stream().filter(k -> k.side() == side).toList();
    }

    /** The qualifier as the target shows it ("est recouverte par" for "recouvre"). */
    public Long mirrorOf(Long conceptId) {
        return mirrorByConcept.getOrDefault(conceptId, conceptId);
    }

    /** The qualifier to store for a concept picked from the target's side: the concept it is the mirror of. */
    public Long qualifierForMirror(Long mirrorConceptId) {
        return mirrorByConcept.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), mirrorConceptId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(mirrorConceptId);
    }
}
