package fr.siamois.ui.api.openapi.v1.service;

import fr.siamois.ui.api.openapi.v1.request.list.FilterOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.List;
import java.util.Locale;

/**
 * The comparisons a list filter's operator ({@link FilterOperator}) makes that do not depend on where
 * the column is — text matches and "has a value" — shared by every column layout of
 * {@link FieldQueryService} (entity property, answer row).
 */
final class FilterPredicates {

    private static final char LIKE_ESCAPE = '\\';

    private FilterPredicates() {
    }

    /**
     * A text column matching at least one of {@code texts}, ignoring case.
     *
     * @param match the text match ({@link FilterOperator.Match#EQUAL}, CONTAINS, STARTS_WITH or ENDS_WITH)
     * @param value the column
     * @param texts the texts, already lowercased
     * @param cb    the criteria builder
     * @return the OR of one comparison per text
     */
    static Predicate textMatches(FilterOperator.Match match, Expression<String> value, List<String> texts,
                                 CriteriaBuilder cb) {
        Expression<String> lowered = cb.lower(value);
        return cb.or(texts.stream()
                .map(text -> match == FilterOperator.Match.EQUAL
                        ? cb.equal(lowered, text)
                        : cb.like(lowered, likePattern(match, text), LIKE_ESCAPE))
                .toArray(Predicate[]::new));
    }

    /**
     * The LIKE pattern of a text match, the text's own wildcards escaped so that « 10% » looks for a
     * percent sign rather than for anything.
     *
     * @param match CONTAINS, STARTS_WITH or ENDS_WITH
     * @param text  the text, lowercased
     * @return the pattern, to use with {@link #LIKE_ESCAPE}
     */
    static String likePattern(FilterOperator.Match match, String text) {
        String escaped = text.replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return switch (match) {
            case STARTS_WITH -> escaped + "%";
            case ENDS_WITH -> "%" + escaped;
            default -> "%" + escaped + "%";
        };
    }

    /**
     * Whether a column has a value: not null, and — for a text — not blank either, since a cleared
     * text answer may be stored as an empty string.
     *
     * @param value the column
     * @param text  whether the column holds text
     * @param cb    the criteria builder
     * @return the predicate
     */
    @SuppressWarnings("unchecked")
    static Predicate hasValue(Expression<?> value, boolean text, CriteriaBuilder cb) {
        if (!text) return cb.isNotNull(value);
        return cb.and(cb.isNotNull(value), cb.notEqual(cb.trim((Expression<String>) value), ""));
    }

    /**
     * The texts a filter compares a column with, lowercased once.
     *
     * @param values the raw values
     * @return the lowercased texts
     */
    static List<String> lowercased(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
    }
}
