package fr.siamois.ui.api.openapi.v1.request.list;

import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * How a list filter compares a column with its values: {@code f.<fieldId>.op=<operator>}, next to
 * the values ({@code f.<fieldId>=…}) or the bounds ({@code f.<fieldId>.from/.to}). Without it, a
 * filter keeps the operator its column always had (contains for a text, between for a number or a
 * date, in for the others).
 *
 * <p>Each operator is a {@link Match} — what a value is compared with — possibly {@link #negated()}:
 * "does not contain" is the rows "contains" leaves out, empty values included, so the two always
 * add up to the whole list.</p>
 */
public enum FilterOperator {

    IS("is", Match.EQUAL, false),
    IS_NOT("is_not", Match.EQUAL, true),
    CONTAINS("contains", Match.CONTAINS, false),
    NOT_CONTAINS("not_contains", Match.CONTAINS, true),
    STARTS_WITH("starts_with", Match.STARTS_WITH, false),
    ENDS_WITH("ends_with", Match.ENDS_WITH, false),
    IN("in", Match.ANY_OF, false),
    NOT_IN("not_in", Match.ANY_OF, true),
    BETWEEN("between", Match.RANGE, false),
    NOT_EMPTY("not_empty", Match.PRESENT, false),
    EMPTY("empty", Match.PRESENT, true);

    /**
     * What a filter compares a column's value with, before any negation.
     */
    public enum Match {
        /** Equal to one of the texts, ignoring case. */
        EQUAL,
        /** Containing one of the texts, ignoring case. */
        CONTAINS,
        /** Starting with one of the texts, ignoring case. */
        STARTS_WITH,
        /** Ending with one of the texts, ignoring case. */
        ENDS_WITH,
        /** One of the ids, flags or constants. */
        ANY_OF,
        /** Within the bounds. */
        RANGE,
        /** Having a value at all: takes no value. */
        PRESENT
    }

    private final String wireName;
    private final Match match;
    private final boolean negated;

    /**
     * Declares an operator.
     *
     * @param wireName its name in {@code f.<fieldId>.op}
     * @param match    what it compares the value with
     * @param negated  whether it keeps the rows the match leaves out
     */
    FilterOperator(String wireName, Match match, boolean negated) {
        this.wireName = wireName;
        this.match = match;
        this.negated = negated;
    }

    /**
     * The operator's name in {@code f.<fieldId>.op}.
     *
     * @return the wire name, lower snake case
     */
    public String wireName() {
        return wireName;
    }

    /**
     * What the operator compares a column's value with.
     *
     * @return the match, before negation
     */
    public Match match() {
        return match;
    }

    /**
     * Whether the operator keeps the rows its match leaves out (empty values included).
     *
     * @return true for the "not" and "empty" operators
     */
    public boolean negated() {
        return negated;
    }

    /**
     * Whether the operator compares the column with values or bounds, rather than only checking
     * that it has a value.
     *
     * @return false for {@link #EMPTY} and {@link #NOT_EMPTY}
     */
    public boolean takesValues() {
        return match != Match.PRESENT;
    }

    /**
     * The operator named {@code wireName}, ignoring case.
     *
     * @param wireName the value of an {@code f.<fieldId>.op} parameter
     * @return the operator, or empty for an unknown name
     */
    public static Optional<FilterOperator> fromWire(@Nullable String wireName) {
        if (wireName == null) return Optional.empty();
        String name = wireName.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(operator -> operator.wireName.equals(name)).findFirst();
    }
}
