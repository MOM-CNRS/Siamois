package fr.siamois.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A page of the tree view's roots (or of one parent's children). Its total counts the rows it pages;
 * {@code matchCount} is the number of entities that actually match the search and filters, each counted
 * once however many branches show it, and without the parents that only lead to a match.
 *
 * <p>It may also carry the tree flags of its rows ({@link #hasChildren}, {@link #isMatch}), for a type
 * whose rows are not DTOs it can set them on (an immutable record, say).</p>
 */
public class TreePage<T> extends PageImpl<T> {

    private final long matchCount;
    @Nullable
    private final Set<Long> withChildren;
    @Nullable
    private final Set<Long> matchIds;

    public TreePage(List<T> content, Pageable pageable, long total, long matchCount) {
        this(content, pageable, total, matchCount, null, null);
    }

    /**
     * @param withChildren the ids of the rows that have children to show, or null when the flags are not carried
     * @param matchIds     the ids of the rows that are matches, or null when every row counts as one (no search)
     */
    public TreePage(List<T> content, Pageable pageable, long total, long matchCount,
                    @Nullable Set<Long> withChildren, @Nullable Set<Long> matchIds) {
        super(content, pageable, total);
        this.matchCount = matchCount;
        this.withChildren = withChildren;
        this.matchIds = matchIds;
    }

    public static <T> TreePage<T> of(Page<T> page, long matchCount) {
        return new TreePage<>(page.getContent(), page.getPageable(), page.getTotalElements(), matchCount);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TreePage<?> that && super.equals(that) && matchCount == that.matchCount
                && java.util.Objects.equals(withChildren, that.withChildren) && java.util.Objects.equals(matchIds, that.matchIds);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), matchCount, withChildren, matchIds);
    }

    public long getMatchCount() {
        return matchCount;
    }

    /** Whether the row with this id has children to show (false when the flags are not carried). */
    public boolean hasChildren(Long id) {
        return withChildren != null && withChildren.contains(id);
    }

    /** Whether the row with this id is a match; with no search every row is one. */
    public boolean isMatch(Long id) {
        return matchIds == null || matchIds.contains(id);
    }
}
