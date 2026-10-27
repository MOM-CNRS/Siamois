import {useEffect} from "react";
import {MOBILE_QUERY} from "./viewport";

// What slides away: the page's top bar and the fiche's breadcrumb. The panel's header only sheds its toolbar and
// secondary chips (main-panel.css, on the attribute set below): the status and the title chip stay, with the tabs.
const CHROME = "#topbar-row, .entity-detail-panel .panel-bc-row, .entity-detail-panel .p-breadcrumb.panel-bc";
// Scroll boxes that count: a panel's own, not a dropdown list or a dialog.
const PANEL = ".entity-list-panel, .entity-detail-panel";
const IGNORED = ".p-overlaypanel, .p-dialog, .p-dropdown-panel, .p-multiselect-panel, .p-menu, .p-tabview-nav-container";

const NEAR_TOP = 24;
const STEP = 10;
// Folding the bars changes the scroll box's height, which can move its scrollTop (a clamp at the bottom, scroll
// anchoring) and read as a scroll the other way: that would unfold them, which moves it again, and so on. So
// scroll events do not decide anything while the bars move, and a move upwards close to the bottom — where the
// box's height decides the position — does not unfold them.
const SETTLE_MS = 300;
const BOTTOM_ZONE = 120;
// A box must scroll by at least this much for folding to make sense.
const MIN_SCROLL_RANGE = 40;
const HEADER_TRIM = 60; // the panel header's toolbar and secondary chips, which leave without an element of their own

function foldableHeight(): number {
  let total = HEADER_TRIM;
  document.querySelectorAll<HTMLElement>(CHROME).forEach((el) => {
    if (!el.matches(".panel-bc-row .p-breadcrumb")) total += el.offsetHeight;
  });
  return total;
}

// The top bar leaves through the top of the screen; the breadcrumb sits under the panel's header, so it folds its own
// height instead — pulling it up would slide it over the header that stays.
function fold(el: HTMLElement, hidden: boolean) {
  if (el.matches("#topbar-row")) {
    el.style.marginTop = hidden ? `${-el.offsetHeight}px` : "";
    return;
  }
  if (hidden) {
    el.style.maxHeight = `${el.offsetHeight}px`;
    el.style.overflow = "hidden";
    void el.offsetHeight; // the starting height must be applied before the transition to 0
    el.style.maxHeight = "0px";
    el.style.paddingTop = "0px";
    el.style.paddingBottom = "0px";
  } else {
    el.style.maxHeight = "";
    el.style.overflow = "";
    el.style.paddingTop = "";
    el.style.paddingBottom = "";
  }
}

function setHidden(hidden: boolean) {
  document.querySelectorAll<HTMLElement>(CHROME).forEach((el) => {
    // A breadcrumb inside its row moves with the row.
    if (el.matches(".panel-bc-row .p-breadcrumb")) return;
    fold(el, hidden);
  });
  if (hidden) document.documentElement.dataset.mobileChrome = "hidden";
  else delete document.documentElement.dataset.mobileChrome;
}

/**
 * Phone width: scrolling a panel down slides the top bar and the breadcrumb off the top of the screen and trims the
 * panel header to its status and title chip — more room for the content — and scrolling back up brings them all
 * back, the way mobile apps do. Always shown near the top of the scroll box.
 */
export function useHideChromeOnScroll(): void {
  useEffect(() => {
    const query = window.matchMedia?.(MOBILE_QUERY);
    if (!query) return;
    let hidden = false;
    // Per scroll box: where it was, and where the current run in one direction started (a slow scroll fires
    // many events of a pixel or two, so the distance is counted over the run, not per event).
    const state = new WeakMap<Element, { top: number; runStart: number; direction: number }>();

    let settledAt = 0;
    // Folding frees the bars' height, which would shorten the box's scroll range by as much — to nothing, for a
    // short page, leaving no way to scroll back up to unfold them. The box gets that much padding at its end
    // while they are folded, so it scrolls exactly as far as before.
    let slackBox: HTMLElement | undefined;
    const addSlack = (box: HTMLElement, freed: number) => {
      removeSlack();
      slackBox = box;
      box.style.paddingBottom = `calc(${getComputedStyle(box).paddingBottom} + ${freed}px)`;
    };
    const removeSlack = () => {
      if (slackBox) slackBox.style.paddingBottom = "";
      slackBox = undefined;
    };
    let pending: ReturnType<typeof setTimeout> | undefined;

    // The run restarts from where the box is once the bars have changed: what came before is spent.
    const show = (next: boolean, box: HTMLElement) => {
      if (next === hidden) return;
      hidden = next;
      settledAt = Date.now() + SETTLE_MS;
      if (next) addSlack(box, foldableHeight());
      else removeSlack();
      setHidden(next);
      state.set(box, { top: box.scrollTop, runStart: box.scrollTop, direction: 0 });
    };

    const decide = (box: HTMLElement) => {
      const current = state.get(box);
      if (!current) return;
      const { top, runStart } = current;
      const range = box.scrollHeight - box.clientHeight;
      if (!hidden && range < MIN_SCROLL_RANGE) return;
      const nearBottom = range - top < BOTTOM_ZONE;
      if (top <= NEAR_TOP) show(false, box);
      else if (top - runStart > STEP) show(true, box);
      else if (runStart - top > STEP && !nearBottom) show(false, box);
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
      // While the bars move nothing is decided — but the movement is still counted, and judged as soon as they
      // have settled: a quick flick that ends within that time must not be lost.
      const wait = settledAt - Date.now();
      if (wait > 0) {
        clearTimeout(pending);
        pending = setTimeout(() => decide(box), wait + 20);
        return;
      }
      decide(box);
    };
    const onChange = () => {
      if (!query.matches && hidden) {
        hidden = false;
        removeSlack();
        setHidden(false);
      }
    };

    document.addEventListener("scroll", onScroll, { capture: true, passive: true });
    query.addEventListener("change", onChange);
    return () => {
      clearTimeout(pending);
      removeSlack();
      document.removeEventListener("scroll", onScroll, { capture: true });
      query.removeEventListener("change", onChange);
      setHidden(false);
    };
  }, []);
}
