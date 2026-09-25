import { useEffect, useRef, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Menu } from "primereact/menu";
import type { MenuItem } from "primereact/menuitem";
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
  // Prev/next buttons, rendered inside the navigation group (before the bookmark).
  navigation?: ReactNode;
  // Overview pane: create/duplicate/settings collapse into a "…" menu.
  compact?: boolean;
}

export function PanelToolbar({ chrome, organizationId, actions, navigation, compact }: PanelToolbarProps) {
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

  const secondary = [
    actions?.create && { label: "Créer", icon: "bi bi-plus-square", command: actions.create },
    actions?.duplicate && { label: "Dupliquer", icon: "bi bi-copy", command: actions.duplicate },
  ].filter(Boolean) as MenuItem[];
  const tertiary = [
    actions?.settings && { label: "Paramètres", icon: "bi bi-gear", command: actions.settings },
  ].filter(Boolean) as MenuItem[];
  const moreMenuRef = useRef<Menu>(null);

  const button = (item: MenuItem) => (
    <Button
      key={item.label}
      icon={item.icon as string}
      className="sideview-topbar-button"
      text
      rounded
      tooltip={item.label}
      tooltipOptions={{ position: "bottom" }}
      aria-label={item.label}
      onClick={() => item.command?.({} as never)}
    />
  );

  return (
    <div className="panel-toolbar" style={{ display: "flex", alignItems: "center", gap: "0.25rem" }}>
      {/* Navigation group: leave focus / close or promote the overview, prev/next, bookmark. */}
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
      {actions?.closeOverview && (
        <Button
          icon="bi bi-chevron-double-right"
          className="sideview-topbar-button"
          text
          rounded
          tooltip="Fermer l'aperçu latéral"
          onClick={actions.closeOverview}
        />
      )}
      {actions?.fullscreen && (
        <Button
          icon="bi bi-arrows-angle-expand"
          className="sideview-topbar-button"
          text
          rounded
          tooltip="Ouvrir en mode focus"
          onClick={actions.fullscreen}
        />
      )}
      {navigation}
      <Button
        icon={bookmarked ? "bi bi-bookmark-fill" : "bi bi-bookmark"}
        className="sideview-topbar-button"
        text
        rounded
        tooltip={bookmarked ? "Retirer des favoris" : "Ajouter aux favoris"}
        tooltipOptions={{ position: "bottom" }}
        disabled={bookmarkMutation.isPending || organizationId == null || chrome.resourceUri === ""}
        onClick={() => bookmarkMutation.mutate()}
      />
      {compact ? (
        // The narrow overview pane: everything past navigation folds into one "…" menu.
        secondary.length + tertiary.length > 0 && (
          <>
            <ToolbarSeparator />
            <Button
              icon="bi bi-three-dots"
              className="sideview-topbar-button"
              text
              rounded
              tooltip="Plus d'actions"
              tooltipOptions={{ position: "bottom" }}
              aria-label="Plus d'actions"
              aria-haspopup
              onClick={(e) => moreMenuRef.current?.toggle(e)}
            />
            <Menu
              ref={moreMenuRef}
              popup
              model={[...secondary, ...(secondary.length && tertiary.length ? [{ separator: true }] : []), ...tertiary]}
            />
          </>
        )
      ) : (
        <>
          {secondary.length > 0 && <ToolbarSeparator />}
          {secondary.map(button)}
          {tertiary.length > 0 && <ToolbarSeparator />}
          {tertiary.map(button)}
        </>
      )}
    </div>
  );
}

function ToolbarSeparator() {
  return <span className="panel-toolbar-separator" role="separator" aria-orientation="vertical" />;
}
