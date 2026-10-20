package fr.siamois.ui.api.openapi.v1.request.list;

import org.springframework.lang.Nullable;

/**
 * The tree-view part of a list request: whether the list is asked as a tree ({@code tree=true}), the row
 * whose children are wanted ({@code parent}, roots when null), the context the tree is computed in beyond
 * the list's own (a place's projects: {@code spatialContextId}), and whether the response should carry
 * {@code meta.matchCount} ({@code matchCount=true}, for the lists whose roots do not already give it).
 */
public record TreeRequest(boolean enabled, @Nullable Long parentId, @Nullable Long spatialContextId, boolean withMatchCount) {

    /** A plain list: no tree. */
    public static final TreeRequest OFF = new TreeRequest(false, null, null, false);

    /** The tree (or, with {@code parentId}, one row's children). */
    public static TreeRequest tree(@Nullable Long parentId) {
        return new TreeRequest(true, parentId, null, false);
    }

    public static TreeRequest of(boolean tree, @Nullable Long parentId) {
        return tree ? tree(parentId) : OFF;
    }

    /** The same request, asking for {@code meta.matchCount} when it is a tree. */
    public TreeRequest withMatchCount(boolean wanted) {
        return new TreeRequest(enabled, parentId, spatialContextId, enabled && wanted);
    }

    /** The same request inside a context (a place's projects). */
    public TreeRequest inSpatialContext(@Nullable Long placeId) {
        return new TreeRequest(enabled, parentId, placeId, withMatchCount);
    }
}
