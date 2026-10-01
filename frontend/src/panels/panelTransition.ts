import { flushSync } from "react-dom";

// The four pane changes that get an animation (styles/main-panel.css keys its directions off this).
export type PanelTransitionKind = "overview-open" | "overview-close" | "focus-enter" | "focus-exit";

type ViewTransitionDocument = Document & {
  startViewTransition?: (update: () => void) => { finished: Promise<void> };
};

/**
 * Runs `update` (React state changes) inside a View Transition: the browser snapshots the panes,
 * React re-renders synchronously (flushSync — the new DOM must exist when the callback returns),
 * and the named panes animate between their old and new boxes — even though opening the overview
 * or entering focus rebuilds the pane structure (single pane ↔ Splitter), which a plain CSS
 * transition can't follow. No support, or prefers-reduced-motion: the update just happens.
 */
export function withPanelTransition(kind: PanelTransitionKind, update: () => void): void {
  const doc = document as ViewTransitionDocument;
  const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
  if (typeof doc.startViewTransition !== "function" || reduceMotion) {
    update();
    return;
  }
  const root = document.documentElement;
  root.dataset.panelTransition = kind;
  const transition = doc.startViewTransition(() => flushSync(update));
  transition.finished.finally(() => {
    if (root.dataset.panelTransition === kind) delete root.dataset.panelTransition;
  });
}

/**
 * A pane's view-transition-name, derived from what it shows: the SAME fiche keeps the same name
 * wherever it's rendered, so entering focus morphs the overview's fiche into the full-width main
 * pane (and back on exit) instead of cross-fading two unrelated panes.
 */
export function paneTransitionName(panelKind: string, entityType: string, entityId?: string | number): string {
  return `pane-${panelKind}-${entityType}-${entityId ?? "all"}`.replace(/[^a-zA-Z0-9_-]/g, "_");
}
