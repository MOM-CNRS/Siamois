import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { createBookmark, deleteBookmark } from "../api/bookmarks";
import type { OverviewActions, PanelActions, PanelChrome } from "../mountOptions";

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
// no new CSS, not "skip PrimeReact's components." sideview-topbar-button rides along via
// className for a future theme pass; the stock lara-light-blue theme is what actually renders
// today.
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
        disabled={bookmarkMutation.isPending || organizationId == null}
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
