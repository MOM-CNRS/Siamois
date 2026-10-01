package fr.siamois.ui.api.openapi.v1.resource;

/**
 * A REST resource that carries its own navigation/bookmark URI ({@code /recording-unit/42}, the same
 * value JSF's {@code AbstractPanel.ressourceUri()} and the bookmark table use) and a {@code bookmarked}
 * flag for the caller — lets {@link fr.siamois.ui.api.openapi.v1.service.ResourceBookmarkService} fill
 * that flag for any entity type, one query per list page.
 */
public interface BookmarkableResource {

    String getResourceUri();

    void setBookmarked(boolean bookmarked);
}
