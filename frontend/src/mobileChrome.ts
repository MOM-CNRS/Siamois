import { useEffect } from "react";
import { MOBILE_QUERY } from "./viewport";

// What slides away: the page's top bar, the panels' header (toolbar, status, chips) and the fiche's breadcrumb.
const CHROME = "#topbar-row, .entity-list-panel.p-panel > .p-panel-header, .entity-detail-panel.p-panel > .p-panel-header, .entity-detail-panel .panel-bc-row, .entity-detail-panel .p-breadcrumb.panel-bc";
// Scroll boxes that count: a panel's own, not a dropdown list or a dialog.
const PANEL = ".entity-list-panel, .entity-detail-panel";
const IGNORED = ".p-overlaypanel, .p-dialog, .p-dropdown-panel, .p-multiselect-panel, .p-menu, .p-tabview-nav-container";

const NEAR_TOP = 24;
const STEP = 10;

function setHidden(hidden: boolean) {
  document.querySelectorAll<HTMLElement>(CHROME).forEach((el) => {
    // A breadcrumb inside its row moves with the row.
    if (el.matches(".panel-bc-row .p-breadcrumb")) return;
    el.style.marginTop = hidden ? `${-el.offsetHeight}px` : "";
  });
}

/**
 * Phone width: scrolling a panel down slides the top bar, the panel header and the breadcrumb off the top of the
 * screen — more room for the content — and scrolling back up brings them back, the way mobile apps do. Always
 * shown near the top of the scroll box.
 */
export function useHideChromeOnScroll(): void {
  useEffect(() => {
    const query = window.matchMedia?.(MOBILE_QUERY);
    if (!query) return;
    let hidden = false;
    // Per scroll box: where it was, and where the current run in one direction started (a slow scroll fires
    // many events of a pixel or two, so the distance is counted over the run, not per event).
    const state = new WeakMap<Element, { top: number; runStart: number; direction: number }>();

    const show = (next: boolean) => {
      if (next === hidden) return;
      hidden = next;
      setHidden(next);
    };
    const onScroll = (event: Event) => {
      if (!query.matches) return;
      const box = event.target;
      if (!(box instanceof HTMLElement) || !box.closest(PANEL) || box.closest(IGNORED)) return;
      const top = box.scrollTop;
      const was = state.get(box) ?? { top, runStart: top, direction: 0 };
      const direction = Math.sign(top - was.top);
      const runStart = direction !== 0 && direction !== was.direction ? was.top : was.runStart;
      state.set(box, { top, runStart, direction: direction || was.direction });
      if (top <= NEAR_TOP) show(false);
      else if (top - runStart > STEP) show(true);
      else if (runStart - top > STEP) show(false);
    };
    const onChange = () => {
      if (!query.matches) show(false);
    };

    document.addEventListener("scroll", onScroll, { capture: true, passive: true });
    query.addEventListener("change", onChange);
    return () => {
      document.removeEventListener("scroll", onScroll, { capture: true });
      query.removeEventListener("change", onChange);
      setHidden(false);
    };
  }, []);
}
