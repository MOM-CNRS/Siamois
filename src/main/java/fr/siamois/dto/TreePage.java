package fr.siamois.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

/**
 * A page of the tree view's roots. Its total counts the roots (it is what pages them); {@code matchCount}
 * is the number of entities that actually match the search and filters, each counted once however many
 * branches show it, and without the parents that only lead to a match.
 */
public class TreePage<T> extends PageImpl<T> {

    private final long matchCount;

    public TreePage(List<T> content, org.springframework.data.domain.Pageable pageable, long total, long matchCount) {
        super(content, pageable, total);
        this.matchCount = matchCount;
    }

    public static <T> TreePage<T> of(Page<T> page, long matchCount) {
        return new TreePage<>(page.getContent(), page.getPageable(), page.getTotalElements(), matchCount);
    }

    public long getMatchCount() {
        return matchCount;
    }
}
