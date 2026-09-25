import type { PanelChrome } from "../mountOptions";

// The one shape every entity's `detail.chrome` derives from — `resourceUri` and `bookmarked` both come
// straight off the REST resource (ResourceBookmarkService server-side), never a hardcoded route
// prefix, so a React bookmark and a JSF one always land on the same bookmark row.
export function bookmarkChrome(
  entity: { resourceUri?: string | null; bookmarked?: boolean },
  title: string | null | undefined,
): PanelChrome {
  return { resourceUri: entity.resourceUri ?? "", title: title ?? "", bookmarked: entity.bookmarked ?? false };
}
