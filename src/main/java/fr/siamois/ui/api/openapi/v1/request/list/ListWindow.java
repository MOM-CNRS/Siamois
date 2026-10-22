package fr.siamois.ui.api.openapi.v1.request.list;

/**
 * The window of a list request: where it starts, how long it is, how it is sorted and what it searches
 * (the {@code offset}, {@code limit}, {@code sort} and {@code search} query parameters, bundled).
 */
public record ListWindow(int offset, int limit, String sort, String search) {
}
