import {useEffect} from "react";
import {placeVertically} from "./viewportPlacement";

// PrimeReact's own marks of an overlay opened over its anchor (its arrow then points down).
const FLIPPED_CLASS = "p-overlaypanel-flipped";
const FLIPPED_ATTRIBUTE = "data-p-overlaypanel-flipped";
// Neither under nor over the anchor: its arrow would point at nothing.
const DETACHED_CLASS = "sia-overlay-detached";

/**
 * Moves the overlay vertically so that it stays entirely inside the window. Only its `top` changes: its
 * horizontal alignment stays PrimeReact's.
 */
function keepInViewport(overlay: HTMLElement, anchor: HTMLElement) {
  const placement = placeVertically(anchor.getBoundingClientRect(), overlay.offsetHeight, window.innerHeight);
  overlay.classList.toggle(FLIPPED_CLASS, placement.side === "above");
  overlay.setAttribute(FLIPPED_ATTRIBUTE, String(placement.side === "above"));
  overlay.classList.toggle(DETACHED_CLASS, placement.side === "detached");
  // Measured after the classes changed (they change the overlay's margin), and applied as a shift of the current
  // `top`: right whatever the overlay is positioned against and however far the page is scrolled.
  const currentTop = Number.parseFloat(overlay.style.top) || 0;
  overlay.style.top = `${currentTop + placement.top - overlay.getBoundingClientRect().top}px`;
}

/**
 * Keeps an open overlay inside the window. PrimeReact places an overlay once, when it opens, from its height at
 * that moment: a form opened low on the screen, or one that grows afterwards (a list unfolding, a second step),
 * would run off the bottom of the window. The overlay is placed again each time its size or the window's changes.
 *
 * @param getOverlay the overlay's element, read once it is shown
 * @param anchor the element the overlay opened next to
 * @param shown whether the overlay is shown (its opening animation over)
 */
export function useKeepInViewport(getOverlay: () => HTMLElement | null | undefined, anchor: HTMLElement | null, shown: boolean) {
  useEffect(() => {
    const overlay = getOverlay();
    if (!shown || !anchor || !overlay) return undefined;
    const place = () => keepInViewport(overlay, anchor);
    place();
    window.addEventListener("resize", place);
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(place);
    observer?.observe(overlay);
    return () => {
      window.removeEventListener("resize", place);
      observer?.disconnect();
    };
  }, [getOverlay, anchor, shown]);
}
