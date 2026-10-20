package fr.siamois.ui.api.openapi.v1.generic.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.siamois.dto.TreePage;
import org.springframework.data.domain.Page;

/**
 * Paging of a list. {@code matchCount}: the tree view only (a page of roots, so {@code total} counts
 * the roots) — the number of entities that match the search and filters, each counted once.
 */
public record ListMeta(Long total, Integer limit, Long offset, @JsonInclude(JsonInclude.Include.NON_NULL) Long matchCount) {

    public ListMeta(Long total, Integer limit, Long offset) {
        this(total, limit, offset, null);
    }

    /** The meta of {@code page}, with its match count when it is a page of the tree view's roots. */
    public static ListMeta of(Page<?> page, int limit, int offset) {
        Long matchCount = page instanceof TreePage<?> tree ? tree.getMatchCount() : null;
        return new ListMeta(page.getTotalElements(), limit, (long) offset, matchCount);
    }
}
