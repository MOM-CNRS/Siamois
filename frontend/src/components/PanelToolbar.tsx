import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { createBookmark, deleteBookmark } from "../api/bookmarks";
import type { OverviewActions, PanelActions, PanelChrome } from "../mountOptions";

// Generic panel titlebar (plan §7.3/§8 phase 8) — one component for both the main panel's
// titlebar (focus.xhtml) and the overview pane's own (panelContent.xhtml): same six actions
// (bookmark/create/duplicate/refresh/settings, plus closeOverview/fullscreen for the overview
// only), same "sideview-topbar-button" class names those templates already use. Nothing here is
// Project-specific — chrome/actions/organizationId all come from MountOptions.
//
// Bookmark is deliberately NOT one of the bridged `actions` (plan §7.3): JSF passes the initial
// `bookmarked` flag once at mount and this component calls the REST bookmark endpoints directly
// on toggle, rather than round-tripping through a remoteCommand.
export interface PanelToolbarProps {
  chrome: PanelChrome;
  organizationId?: number;
  actions?: PanelActions | OverviewActions;
}

export function PanelToolbar({ chrome, organizationId, actions }: PanelToolbarProps) {
  const [bookmarked, setBookmarked] = useState(chrome.bookmarked);

  const bookmarkMutation = useMutation({
    mutationFn: () => {
      if (organizationId == null) {
        throw new Error("organizationId is required to bookmark this panel");
      }
      return bookmarked
        ? deleteBookmark(chrome.resourceUri, organizationId)
        : createBookmark({ resourceUri: chrome.resourceUri, titleCode: chrome.title, organizationId });
    },
    onSuccess: () => setBookmarked((current) => !current),
  });

  const overview = actions as OverviewActions | undefined;

  return (
    <div className="panel-toolbar" style={{ display: "flex", gap: "0.5rem" }}>
      {overview?.closeOverview && (
        <button
          type="button"
          className="sideview-topbar-button"
          title="Fermer l'aperçu latéral"
          onClick={overview.closeOverview}
        >
          <i className="bi bi-chevron-double-right" />
        </button>
      )}
      {overview?.fullscreen && (
        <button
          type="button"
          className="sideview-topbar-button"
          title="Ouvrir en mode focus"
          onClick={overview.fullscreen}
        >
          <i className="bi bi-arrows-angle-expand" />
        </button>
      )}
      <button
        type="button"
        className="sideview-topbar-button"
        disabled={bookmarkMutation.isPending || organizationId == null}
        onClick={() => bookmarkMutation.mutate()}
      >
        <i className={bookmarked ? "ui-icon bi bi-bookmark-fill" : "ui-icon bi bi-bookmark"} />
      </button>
      {actions?.create && (
        <button type="button" className="sideview-topbar-button" title="Créer" onClick={actions.create}>
          <i className="bi bi-plus-square" />
        </button>
      )}
      {actions?.duplicate && (
        <button type="button" className="sideview-topbar-button" title="Dupliquer" onClick={actions.duplicate}>
          <i className="bi bi-copy" />
        </button>
      )}
      {actions?.refresh && (
        <button
          type="button"
          className="sideview-topbar-button"
          title="Rafraîchir"
          onClick={actions.refresh}
        >
          <i className="bi bi-arrow-clockwise" />
        </button>
      )}
      {actions?.settings && (
        <button type="button" className="sideview-topbar-button" title="Paramètres" onClick={actions.settings}>
          <i className="bi bi-gear" />
        </button>
      )}
    </div>
  );
}
