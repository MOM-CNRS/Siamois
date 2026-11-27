package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationDefinition;
import fr.siamois.domain.models.relation.RelationEndKind;
import fr.siamois.domain.models.relation.RelationSide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The values of a configurable relation field: the links of its definition seen from the field's side, for owners
 * of one kind (docs/modele-relations.md, §3.5). Their other end may be of any kind, so the values are read with one
 * branch per kind, each joined to its table for its label; {@code role} carries the API resourceType of the value.
 * <p>
 * A complex relation's qualifier is read as the owner shows it: as stored from the source, and through the mirror
 * pairs of the rules the link's project follows (its own, else its institution's, else the platform default) from
 * the target. A symmetric relation reads both ends.
 *
 * @param side the side of the field; ignored when the definition is symmetric
 */
public record DefinitionRelation(long definitionId, RelationSide side, boolean symmetric, boolean complex,
                                 RelationEndKind ownerKind) implements RelationSource {

    /** Each kind a link can reach, with its table, id column and label. Places are not accepted yet. */
    private static final String FULL_IDENTIFIER = "t.full_identifier";
    private static final String IDENTIFIER = "t.identifier";

    private static final Map<RelationEndKind, String[]> TABLES = Map.of(
            RelationEndKind.RECORDING_UNIT, new String[]{"recording_unit", "recording_unit_id", FULL_IDENTIFIER, "recording-units"},
            RelationEndKind.SPECIMEN, new String[]{"specimen", "specimen_id", FULL_IDENTIFIER, "finds"},
            RelationEndKind.PHASE, new String[]{"phase", "phase_id", IDENTIFIER, "phases"},
            RelationEndKind.CONTAINER, new String[]{"container", "container_id", IDENTIFIER, "containers"},
            RelationEndKind.DOCUMENT, new String[]{"siamois_document", "document_id", IDENTIFIER, "documents"},
            RelationEndKind.STUDY, new String[]{"study", "study_id", IDENTIFIER, "studies"},
            RelationEndKind.ACTION_UNIT, new String[]{"action_unit", "action_unit_id", FULL_IDENTIFIER, "projects"});

    /**
     * The mirror of the stored qualifier in the rule set the link's project follows: the project's own, else its
     * institution's, else the platform default.
     */
    static final String MIRROR_JOIN = "LEFT JOIN LATERAL (SELECT m.fk_mirror_concept_id AS mirror_id "
            + "FROM relation_rule_mirror_concept m WHERE m.fk_concept_id = l.fk_qualifier_concept_id "
            + "AND m.fk_relation_rule_id = (SELECT r.relation_rule_id FROM relation_rule r "
            + "JOIN action_unit au ON au.action_unit_id = l.fk_action_unit_id "
            + "WHERE r.fk_relation_definition_id = l.fk_relation_definition_id "
            + "AND (r.fk_action_unit_id = au.action_unit_id "
            + "OR (r.fk_action_unit_id IS NULL AND r.fk_institution_id = au.fk_institution_id) "
            + "OR r.fk_institution_id IS NULL) "
            + "ORDER BY r.fk_action_unit_id IS NULL, r.fk_institution_id IS NULL LIMIT 1)) mq ON TRUE ";

    public static DefinitionRelation of(RelationDefinition definition, RelationSide side, RelationEndKind ownerKind) {
        return new DefinitionRelation(definition.getId(), side, definition.isSymmetric(), !definition.isSimple(),
                ownerKind);
    }

    @Override
    public String valuesSql() {
        List<String> branches = new ArrayList<>();
        for (Map.Entry<RelationEndKind, String[]> other : TABLES.entrySet()) {
            if (symmetric || side == RelationSide.SOURCE) branches.add(branch(true, other.getKey(), other.getValue()));
            if (symmetric || side == RelationSide.TARGET) branches.add(branch(false, other.getKey(), other.getValue()));
        }
        return String.join(" UNION ALL ", branches);
    }

    /** The links whose owner is at the source ({@code fromSource}) or at the target, and whose other end is of one kind. */
    private String branch(boolean fromSource, RelationEndKind otherKind, String[] table) {
        String owner = fromSource ? "source" : "target";
        String other = fromSource ? "target" : "source";
        boolean mirrored = complex && !fromSource && !symmetric;
        String concept;
        if (!complex) {
            concept = "CAST(NULL AS bigint)";
        } else if (mirrored) {
            concept = "COALESCE(mq.mirror_id, l.fk_qualifier_concept_id)";
        } else {
            concept = "l.fk_qualifier_concept_id";
        }
        return "SELECT l." + owner + "_id AS owner_id, l." + other + "_id AS target_id, " + table[2] + " AS label, "
                + "CAST('" + table[3] + "' AS text) AS role, " + concept + " AS concept_id, "
                + "c.external_id AS concept_external_id, CAST(NULL AS boolean) AS asynchronous, "
                + "CAST(NULL AS boolean) AS concept_direction, l.uncertain AS uncertain, l.relation_link_id AS link_id "
                + "FROM relation_link l "
                + "JOIN " + table[0] + " t ON t." + table[1] + " = l." + other + "_id "
                + (mirrored ? MIRROR_JOIN : "")
                + "LEFT JOIN concept c ON c.concept_id = " + concept + " "
                + "WHERE l.fk_relation_definition_id = " + definitionId
                + " AND l." + owner + "_kind = '" + ownerKind.name() + "'"
                + " AND l." + other + "_kind = '" + otherKind.name() + "'";
    }

    /** Varies by value: each row's {@code role} is its resourceType. */
    @Override
    public String resourceType() {
        return null;
    }

    /** Every link has its uncertainty, and a complex one its qualifier. */
    @Override
    public boolean qualified() {
        return true;
    }

    /** Written through /relation-links, never through the owner's answers. */
    @Override
    public boolean readOnly() {
        return true;
    }

    /** The kind of record an owner class is, for the classes that can own a relation field. */
    public static RelationEndKind kindOfOwner(Class<?> ownerType) {
        return switch (ownerType.getSimpleName()) {
            case "RecordingUnit" -> RelationEndKind.RECORDING_UNIT;
            case "Specimen" -> RelationEndKind.SPECIMEN;
            case "Phase" -> RelationEndKind.PHASE;
            case "Container" -> RelationEndKind.CONTAINER;
            case "Document" -> RelationEndKind.DOCUMENT;
            case "Study" -> RelationEndKind.STUDY;
            case "ActionUnit" -> RelationEndKind.ACTION_UNIT;
            default -> throw new IllegalArgumentException("No relation owner kind for " + ownerType.getName());
        };
    }
}
