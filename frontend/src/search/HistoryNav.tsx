import { useState, useSyncExternalStore } from "react";
import { Tooltip } from "primereact/tooltip";
import { listHistory, type HistoryShortcut } from "../api/homeShortcuts";
import { getEntityType } from "../entities/registry";
import { t } from "../i18n";
import { getNavigation } from "../navigation/externalBus";
import { moveTrail, nextEntry, previousEntry, subscribeTrail, trailVersion, type TrailEntry } from "../navigation/navTrail";
import { resolveEntityPath } from "../navigation/paths";
import { apiUrl } from "../api/basePath";

const HISTORY_LOOKUP_LIMIT = 50;

/** What a page is called when the history has no title for it: its type's name (the list) or « type id ». */
function fallbackTitle(entry: TrailEntry): string {
  const path = entry.overviewPath ?? entry.mainPath;
  const resolved = resolveEntityPath(path);
  const config = resolved ? getEntityType(resolved.entityType) : undefined;
  if (!config) return path;
  return resolved?.entityId != null ? `${config.labels.singular} ${resolved.entityId}` : config.labels.plural;
}

/** The title the user's history gives the page (its fiche's, else the main pane's), when it has it. */
function titleIn(history: HistoryShortcut[], entry: TrailEntry): string | undefined {
  return history.find((h) => h.mainUri === entry.mainPath && (h.secondaryUri ?? undefined) === entry.overviewPath)?.title;
}

/**
 * The header's Back / Forward arrows, between the organisation select and the search: they walk the trail of
 * pages the user went through (navigation/navTrail). An arrow with no page behind it is greyed; its tooltip
 * names the page it leads to.
 */
export function HistoryNav({ organizationId }: Readonly<{ organizationId?: number }>) {
  useSyncExternalStore(subscribeTrail, trailVersion);
  const [history, setHistory] = useState<HistoryShortcut[]>([]);
  const previous = previousEntry();
  const next = nextEntry();

  // The titles come from the server's history, read when the user points at an arrow (they are only wanted then).
  function loadTitles() {
    if (organizationId == null) return;
    listHistory(organizationId, HISTORY_LOOKUP_LIMIT)
      .then(setHistory)
      .catch(() => undefined);
  }

  function go(delta: -1 | 1) {
    const target = moveTrail(delta);
    if (!target) return;
    const navigation = getNavigation();
    if (navigation) navigation.restoreUrl(target.url);
    else window.location.assign(apiUrl(target.url));
  }

  const label = (entry: TrailEntry | undefined, key: "nav.back.to" | "nav.forward.to", plain: string) =>
    entry ? t(key, { title: titleIn(history, entry) ?? fallbackTitle(entry) }) : plain;

  return (
    <div className="sia-history-nav">
      {/* Not the browser's own `title`: its delay (about a second) is too long for a name the user wants at a glance. */}
      <Tooltip target=".sia-history-nav-button" position="bottom" showDelay={120} hideDelay={0} />
      <button
        type="button"
        className="sia-history-nav-button"
        disabled={!previous}
        data-pr-tooltip={label(previous, "nav.back.to", t("nav.back"))}
        aria-label={t("nav.back")}
        onMouseEnter={loadTitles}
        onFocus={loadTitles}
        onClick={() => go(-1)}
      >
        <i className="bi bi-chevron-left" />
      </button>
      <button
        type="button"
        className="sia-history-nav-button"
        disabled={!next}
        data-pr-tooltip={label(next, "nav.forward.to", t("nav.forward"))}
        aria-label={t("nav.forward")}
        onMouseEnter={loadTitles}
        onFocus={loadTitles}
        onClick={() => go(1)}
      >
        <i className="bi bi-chevron-right" />
      </button>
    </div>
  );
}
