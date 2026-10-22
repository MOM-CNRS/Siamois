import { useEffect, useRef } from "react";
import type { EntitySibling } from "../../entities/shared/types";

// What a swipe must look like to count: far enough, and clearly horizontal rather than a scroll.
const MIN_DISTANCE = 70;
const MAX_VERTICAL_RATIO = 0.6;
const MAX_DURATION_MS = 700;

// A swipe that starts in one of these belongs to it (a table or tab strip scrolling sideways, a map pan, a slider).
const OWN_GESTURE = "input, textarea, select, [contenteditable='true'], .p-slider, .p-datatable-wrapper, .p-treetable-wrapper, .p-tabview-nav-container, .leaflet-container, canvas, [data-no-swipe]";

function scrollsHorizontally(el: HTMLElement | null, boundary: HTMLElement): boolean {
  for (let node = el; node && node !== boundary; node = node.parentElement) {
    const overflowX = getComputedStyle(node).overflowX;
    if ((overflowX === "auto" || overflowX === "scroll") && node.scrollWidth > node.clientWidth) return true;
  }
  return false;
}

/**
 * On a touch screen, a horizontal swipe over the fiche walks the same list as the header's arrows: left for the
 * next fiche, right for the previous one. Taken over from nothing else — a swipe that starts in a field, a table, a
 * map or any sideways-scrolling box is left to it.
 */
export function useSwipeSiblings(
  element: () => HTMLElement | null | undefined,
  { previous, next, go, enabled }: { previous?: EntitySibling | null; next?: EntitySibling | null; go: (sibling: EntitySibling) => void; enabled: boolean },
): void {
  const latest = useRef({ previous, next, go });
  latest.current = { previous, next, go };

  useEffect(() => {
    const root = element();
    if (!root || !enabled) return;
    let start: { x: number; y: number; time: number } | null = null;

    const onStart = (e: TouchEvent) => {
      const target = e.target as HTMLElement | null;
      const touch = e.touches[0];
      const own = e.touches.length !== 1 || !touch || target?.closest(OWN_GESTURE) || scrollsHorizontally(target, root);
      start = own ? null : { x: touch.clientX, y: touch.clientY, time: Date.now() };
    };
    const onEnd = (e: TouchEvent) => {
      const from = start;
      start = null;
      const touch = e.changedTouches[0];
      if (!from || !touch) return;
      const dx = touch.clientX - from.x;
      const dy = touch.clientY - from.y;
      if (Math.abs(dx) < MIN_DISTANCE || Math.abs(dy) > Math.abs(dx) * MAX_VERTICAL_RATIO || Date.now() - from.time > MAX_DURATION_MS) return;
      const target = dx < 0 ? latest.current.next : latest.current.previous;
      if (target) latest.current.go(target);
    };
    const onCancel = () => {
      start = null;
    };

    root.addEventListener("touchstart", onStart, { passive: true });
    root.addEventListener("touchend", onEnd, { passive: true });
    root.addEventListener("touchcancel", onCancel, { passive: true });
    return () => {
      root.removeEventListener("touchstart", onStart);
      root.removeEventListener("touchend", onEnd);
      root.removeEventListener("touchcancel", onCancel);
    };
  }, [element, enabled]);
}
