package fr.siamois.ui.api.openapi.v1.request.list;

import fr.siamois.dto.FilterDTO;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.annotation.*;
import java.util.Locale;

/**
 * How a list's {@code search} matches — the {@code searchMode} query parameter: {@code contains}
 * (the default, what a list's search box does) or {@code prefix} (what a reference field's picker
 * does: {@code EXEMPLE} finds {@code EXEMPLE.1}, not {@code AUTRE-EXEMPLE}).
 * <p>
 * Read from the current request rather than threaded through every list signature, like
 * {@link ValuesLimit}: the search becomes a filter several layers below the endpoints that accept
 * it. {@link Param} is what documents it on each of those endpoints
 * ({@code SearchModeOperationCustomizer}).
 */
public enum SearchMode {
    CONTAINS,
    PREFIX;

    public static final String PARAM = "searchMode";

    /**
     * The mode the current request asks for, {@link #CONTAINS} if it doesn't say or there is no request.
     */
    @NonNull
    public static SearchMode fromRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return CONTAINS;
        }
        return parse(attributes.getRequest().getParameter(PARAM));
    }

    /**
     * The mode {@code raw} names, {@link #CONTAINS} when blank.
     *
     * @throws ResponseStatusException 400 when {@code raw} names no mode
     */
    @NonNull
    static SearchMode parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return CONTAINS;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "contains" -> CONTAINS;
            case "prefix" -> PREFIX;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    PARAM + " invalide (attendu : contains ou prefix) : " + raw);
        };
    }

    /**
     * Adds the list's {@code search} on {@code column} to {@code filter}, matching the way the current
     * request asks; nothing when {@code search} is blank.
     */
    public static void addSearch(@NonNull FilterDTO filter, @NonNull String column, @Nullable String search) {
        if (search != null && !search.isBlank()) {
            filter.add(column, search, fromRequest().filterType());
        }
    }

    /**
     * The filter type a search in this mode becomes.
     */
    @NonNull
    public FilterDTO.FilterType filterType() {
        return this == PREFIX ? FilterDTO.FilterType.START_WITH : FilterDTO.FilterType.CONTAINS;
    }

    /**
     * Marks an endpoint whose {@code search} honours {@code searchMode}, so the OpenAPI documents it.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Param {
    }
}
