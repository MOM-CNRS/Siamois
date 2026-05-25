package fr.siamois.ui.utils;


import fr.siamois.dto.view.FilterState;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper centralisé pour construire des résumés textuels
 * des filtres appliqués (tooltip, favoris, sidebar, etc.).
 */
public final class FilterStateTooltipHelper {

    private FilterStateTooltipHelper() {
        // utility class
    }

    /**
     * Build tooltip summary from filter states.
     */
    public static String buildTooltip(Map<String, FilterState> states) {

        if (states == null || states.isEmpty()) {
            return "Aucun filtre actif";
        }

        StringBuilder sb = new StringBuilder();

        for (FilterState state : states.values()) {

            if (state == null || state.getValue() == null) {
                continue;
            }

            String line = formatState(state);

            if (line != null && !line.isBlank()) {
                sb.append("\n• ").append(line);
            }
        }

        return sb.toString();
    }

    /**
     * Format a single FilterState into human-readable text.
     */
    private static String formatState(FilterState state) {

        return switch (state.getType()) {

            case TEXT -> formatText(state);

            case CONCEPT -> formatEntityList(state, "Concept");

            case PERSON -> formatEntityList(state, "Personne");

            case ACTION_UNIT -> formatEntityList(state, "Action");

            case SPATIAL_UNIT -> formatEntityList(state, "Unité spatiale");

            case DATE_RANGE -> formatDateRange(state);

            case BOOLEAN -> formatBoolean(state);

            default -> null;
        };
    }

    /**
     * TEXT filter
     */
    private static String formatText(FilterState state) {
        Object value = state.getValue();
        return value != null ? "Texte : " + value : null;
    }

    /**
     * Generic formatter for entity filters (concept/person/etc.)
     */
    @SuppressWarnings("unchecked")
    private static String formatEntityList(FilterState state, String prefix) {

        Object raw = state.getValue();

        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }

        List<String> labels = list.stream()
                .filter(Objects::nonNull)
                .map(v -> {
                    if (v instanceof Map<?, ?> map) {
                        Object label = map.get("label");
                        return label != null ? label.toString() : null;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();

        if (labels.isEmpty()) {
            return null;
        }

        return prefix + " : " + joinWithLimit(labels, 5);
    }

    /**
     * DATE RANGE formatter
     */
    @SuppressWarnings("unchecked")
    private static String formatDateRange(FilterState state) {

        Object raw = state.getValue();

        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return null;
        }

        List<Date> dates = (List<Date>) list;

        Date from = dates.size() > 0 ? dates.get(0) : null;
        Date to = dates.size() > 1 ? dates.get(1) : null;

        if (from != null && to != null) {
            return "Date : " + from + " → " + to;
        }

        if (from != null) {
            return "Date après : " + from;
        }

        if (to != null) {
            return "Date avant : " + to;
        }

        return null;
    }

    /**
     * BOOLEAN formatter
     */
    private static String formatBoolean(FilterState state) {
        Object value = state.getValue();
        return value != null ? "Booléen : " + value : null;
    }

    /**
     * Join list with truncation support.
     */
    private static String joinWithLimit(List<String> values, int max) {

        if (values == null || values.isEmpty()) {
            return "";
        }

        if (values.size() <= max) {
            return String.join(", ", values);
        }

        List<String> limited = values.subList(0, max);

        int remaining = values.size() - max;

        return String.join(", ", limited) + " et " + remaining + " autre(s)";
    }

    /**
     * Optional helper if you want ordered output (recommended UX improvement).
     */
    public static String buildOrderedTooltip(Map<String, FilterState> states) {

        if (states == null || states.isEmpty()) {
            return "Aucun filtre actif";
        }

        List<FilterState> ordered = states.values().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FilterState::getType))
                .toList();

        StringBuilder sb = new StringBuilder("Filtres actifs :");

        for (FilterState state : ordered) {

            if (state.getValue() == null) continue;

            String line = formatState(state);

            if (line != null) {
                sb.append("\n• ").append(line);
            }
        }

        return sb.toString();
    }
}