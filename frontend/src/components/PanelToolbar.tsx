import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { createBookmark, deleteBookmark, getBookmarkStatus } from "../api/bookmarks";
import type { PanelActions, PanelChrome } from "../mountOptions";

// Generic panel titlebar (plan §7.3/§8 phase 8) — one component for both the main panel's
// titlebar (focus.xhtml) and the overview pane's own (panelContent.xhtml): same actions
// (bookmark/create/duplicate/settings, plus closeOverview/fullscreen for the overview
// only, and closeFocus for the main panel in focus mode), same "sideview-topbar-button" class
// names those templates already use. Nothing here is Project-specific — chrome/actions/organizationId all come from MountOptions.
//
// Bookmark is deliberately NOT one of the bridged `actions` (plan §7.3): JSF passes the initial
// `bookmarked` flag once at mount and this component calls the REST bookmark endpoints directly
// on toggle, rather than round-tripping through a remoteCommand.
//
// Buttons are PrimeReact's own <Button>, not plain <button> — "no custom theme" (plan §3) means
// no new CSS, not "skip PrimeReact's components." sideview-topbar-button is JSF's own class
// (focus.xhtml / panelContent.xhtml toolbar buttons), styled for both by the shared theme.
export interface PanelToolbarProps {
  chrome: PanelChrome;
  organizationId?: number;
  actions?: PanelActions;
}

export function PanelToolbar({ chrome, organizationId, actions }: PanelToolbarProps) {
  // chrome.bookmarked is unknown for a list/Home reached client-side — ask the server then.
  const statusKnown = chrome.bookmarked !== undefined;
  const { data: fetchedBookmarked } = useQuery({
    queryKey: ["bookmark-status", chrome.resourceUri, organizationId],
    queryFn: () => getBookmarkStatus(chrome.resourceUri, organizationId!),
    enabled: !statusKnown && organizationId != null && chrome.resourceUri !== "",
  });
  const serverBookmarked = chrome.bookmarked ?? fetchedBookmarked ?? false;
  const [bookmarked, setBookmarked] = useState(serverBookmarked);
  // The chrome can change under a mounted toolbar — a placeholder replaced by the entity's own
  // config.detail.chrome once its data loads, or another entity entirely — so the server's flag
  // wins whenever it (or the target) changes. A local toggle leaves both deps untouched, so it
  // isn't overwritten by the stale value still sitting in the query cache.
  useEffect(() => {
    setBookmarked(serverBookmarked);
  }, [chrome.resourceUri, serverBookmarked]);

  const queryClient = useQueryClient();
  const bookmarkMutation = useMutation({
    mutationFn: () => {
      if (organizationId == null) {
        throw new Error("organizationId is required to bookmark this panel");
      }
      return bookmarked
        ? deleteBookmark(chrome.resourceUri, organizationId)
        : createBookmark({ resourceUri: chrome.resourceUri, titleCode: chrome.title, organizationId });
    },
    onSuccess: () => {
      setBookmarked((current) => !current);
      // Every cached copy of this flag is now stale: the entity's own `bookmarked` (detail and
      // list rows) and a list/Home status lookup.
      void queryClient.invalidateQueries({ queryKey: ["bookmark-status"] });
      void queryClient.invalidateQueries({ queryKey: ["entity-detail"] });
      void queryClient.invalidateQueries({ queryKey: ["entity-list"] });
    },
  });

  const overview = actions;

  return (
    <div className="panel-toolbar" style={{ display: "flex", gap: "0.5rem" }}>
      {actions?.closeFocus && (
        <Button
          icon="bi bi-arrows-angle-contract"
          className="sideview-topbar-button"
          text
          rounded
          tooltip="Fermer le mode focus"
          onClick={actions.closeFocus}
        />
      )}
      {overview?.closeOverview && (
        <Button
          icon="bi bi-chevron-double-right"
          className="sideview-topbar-button"
          text
          rounded
          tooltip="Fermer l'aperçu latéral"
          onClick={overview.closeOverview}
        />
      )}
      {overview?.fullscreen && (
        <Button
          icon="bi bi-arrows-angle-expand"
          className="sideview-topbar-button"
          text
          rounded
          tooltip="Ouvrir en mode focus"
          onClick={overview.fullscreen}
        />
      )}
      <Button
        icon={bookmarked ? "bi bi-bookmark-fill" : "bi bi-bookmark"}
        className="sideview-topbar-button"
        text
        rounded
        disabled={bookmarkMutation.isPending || organizationId == null || chrome.resourceUri === ""}
        onClick={() => bookmarkMutation.mutate()}
      />
      {actions?.create && (
        <Button
          icon="bi bi-plus-square"
          className="sideview-topbar-button"
          text
          rounded
          tooltip="Créer"
          onClick={actions.create}
        />
      )}
      {actions?.duplicate && (
        <Button
          icon="bi bi-copy"
          className="sideview-topbar-button"
          text
          rounded
          tooltip="Dupliquer"
          onClick={actions.duplicate}
        />
      )}
      {actions?.settings && (
        <Button
          icon="bi bi-gear"
          className="sideview-topbar-button"
          text
          rounded
          tooltip="Paramètres"
          onClick={actions.settings}
        />
      )}
    </div>
  );
}
