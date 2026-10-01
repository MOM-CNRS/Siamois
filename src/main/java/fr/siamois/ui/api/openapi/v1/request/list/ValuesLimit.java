package fr.siamois.ui.api.openapi.v1.request.list;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * How many values of each multi-valued answer a response carries — the {@code valuesLimit} query
 * parameter every response with {@code answers} accepts.
 * <p>
 * Read from the current request rather than threaded through every controller signature: the
 * answers are projected several layers below the ~15 endpoints that serve them, all of which
 * accept the parameter the same way. {@link Param} is what documents it on each of those
 * endpoints ({@code ValuesLimitOperationCustomizer}).
 */
public final class ValuesLimit {

    public static final String PARAM = "valuesLimit";
    /** A list shows one value per cell, and a total. */
    public static final int LIST_DEFAULT = 1;
    /** A detail shows a relation's first values; past this, its values are paged separately. */
    public static final int DETAIL_DEFAULT = 50;
    public static final int MAX = 200;
    /** Every value — never from a request: only the values endpoint's own fallback reads it. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private ValuesLimit() {
    }

    /** The limit a list request asks for, {@value #LIST_DEFAULT} if it doesn't say. */
    public static int forList() {
        return fromRequest(LIST_DEFAULT);
    }

    /** The limit a detail request asks for, {@value #DETAIL_DEFAULT} if it doesn't say. */
    public static int forDetail() {
        return fromRequest(DETAIL_DEFAULT);
    }

    static int fromRequest(int fallback) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return fallback;
        }
        HttpServletRequest request = attributes.getRequest();
        return parse(request.getParameter(PARAM), fallback);
    }

    static int parse(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int limit = Integer.parseInt(raw.trim());
            if (limit >= 0 && limit <= MAX) return limit;
        } catch (NumberFormatException ignored) {
            // falls through to the 400 below
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                PARAM + " invalide (attendu : entier entre 0 et " + MAX + ") : " + raw);
    }

    /**
     * Marks an endpoint whose response carries {@code answers}, so the OpenAPI documents the
     * {@code valuesLimit} parameter it accepts, with this endpoint's default.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Param {
        int defaultValue();
    }
}
