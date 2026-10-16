package fr.siamois.infrastructure.database.repositories.relation;

import fr.siamois.domain.models.relation.RelationEntityKind;
import fr.siamois.domain.models.relation.SystemRelations;
import org.springframework.lang.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One side of a configurable relation, read as a relation field: the links of a definition seen from one end.
 * <p>
 * Described by a field's {@code valueBinding}, {@code relation:<definition code>:<reading>:<far kind>} — the one
 * property the whole field machinery already identifies a field by. {@code reading} is the end the owner is at:
 * {@link Reading#SOURCE} (the owner is the links' source, the values their targets), {@link Reading#TARGET} (the
 * mirror: the owner is the target, the values the sources) or {@link Reading#BOTH} (a symmetric relation, read
 * from either end).
 * <p>
 * Each value carries the link's type as seen from the owner's end, and whether the link is flagged uncertain (an
 * attribute of the link's own form). The definition is found by its code inside the SQL, so no lookup precedes a
 * read; a code can only be made of letters, digits, dots, underscores and dashes, which is what keeps it safe to inline.
 */
public final class DefinitionRelation implements RelationSource {

    public static final String PREFIX = "relation:";
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_.-]+");

    public enum Reading {
        SOURCE, TARGET, BOTH
    }

    private final String code;
    private final Reading reading;
    private final RelationEntityKind ownerKind;
    private final RelationEntityKind farKind;

    private DefinitionRelation(String code, Reading reading, RelationEntityKind ownerKind, RelationEntityKind farKind) {
        this.code = code;
        this.reading = reading;
        this.ownerKind = ownerKind;
        this.farKind = farKind;
    }

    /** The {@code valueBinding} of a field that reads a definition's links. */
    public static String bindingOf(String code, Reading reading, RelationEntityKind farKind) {
        return PREFIX + code + ":" + reading + ":" + farKind;
    }

    public static boolean isBinding(@Nullable String binding) {
        return binding != null && binding.startsWith(PREFIX);
    }

    /** The relation a binding describes for an owner of {@code ownerKind}; empty for any other binding, or a malformed one. */
    public static Optional<DefinitionRelation> parse(@Nullable RelationEntityKind ownerKind, @Nullable String binding) {
        if (ownerKind == null || !isBinding(binding)) return Optional.empty();
        String[] parts = binding.substring(PREFIX.length()).split(":");
        if (parts.length != 3 || !CODE.matcher(parts[0]).matches()) return Optional.empty();
        try {
            Reading reading = Reading.valueOf(parts[1]);
            RelationEntityKind far = RelationEntityKind.valueOf(parts[2]);
            if (reading == Reading.BOTH && far != ownerKind) return Optional.empty();
            if (reading == Reading.TARGET && !far.canBeSource()) return Optional.empty();
            if (reading != Reading.TARGET && !ownerKind.canBeSource()) return Optional.empty();
            return Optional.of(new DefinitionRelation(parts[0], reading, ownerKind, far));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String code() {
        return code;
    }

    public Reading reading() {
        return reading;
    }

    public RelationEntityKind ownerKind() {
        return ownerKind;
    }

    public RelationEntityKind farKind() {
        return farKind;
    }

    @Override
    public String pairsSql() {
        return switch (reading) {
            case SOURCE -> oneWay("source", ownerKind, "target", farKind);
            case TARGET -> oneWay("target", ownerKind, "source", farKind);
            case BOTH -> oneWay("source", ownerKind, "target", farKind) + " UNION ALL " + oneWay("target", ownerKind, "source", farKind);
        };
    }

    /** The links of the definition whose {@code ownerSide} end is a {@code owner} and {@code farSide} end a {@code far}. */
    private String oneWay(String ownerSide, RelationEntityKind owner, String farSide, RelationEntityKind far) {
        return "SELECT l.fk_" + ownerSide + "_" + owner.columnSuffix() + "_id AS owner_id, "
                + "l.fk_" + farSide + "_" + far.columnSuffix() + "_id AS target_id, "
                + "CAST('" + ownerSide + "' AS text) AS role, l.fk_" + ownerSide + "_type_concept_id AS concept_id, "
                + "CAST(NULL AS boolean) AS asynchronous, CAST(NULL AS boolean) AS concept_direction, u.value AS uncertain "
                + "FROM relation_link l "
                + "JOIN relation_definition d ON d.relation_definition_id = l.fk_relation_definition_id AND d.code = '" + code + "' "
                + "LEFT JOIN (" + UNCERTAIN_SQL + ") u ON u.link_id = l.relation_link_id "
                + "WHERE l.fk_" + ownerSide + "_" + owner.columnSuffix() + "_id IS NOT NULL "
                + "AND l.fk_" + farSide + "_" + far.columnSuffix() + "_id IS NOT NULL";
    }

    /** Each link's "uncertain" attribute: a boolean answer of the link's own form. */
    private static final String UNCERTAIN_SQL = "SELECT fca.fk_relation_link_id AS link_id, a.value_as_boolean AS value "
            + "FROM form_config_answer fca JOIN custom_field_answer a ON a.fk_form_config_answer_id = fca.form_config_answer_id "
            + "WHERE fca.fk_relation_link_id IS NOT NULL AND a.fk_custom_field_id = " + SystemRelations.UNCERTAIN_FIELD_ID;

    @Override
    public String targetTable() {
        return RelationTarget.of(farKind).table;
    }

    @Override
    public String targetKey() {
        return RelationTarget.of(farKind).key;
    }

    @Override
    public String targetLabel() {
        return RelationTarget.of(farKind).label;
    }

    @Override
    public String resourceType() {
        return RelationTarget.of(farKind).resourceType;
    }

    @Override
    public boolean qualified() {
        return true;
    }

    /** Read-only until a link can be written through the owner's own answers. */
    @Override
    public boolean readOnly() {
        return true;
    }
}
