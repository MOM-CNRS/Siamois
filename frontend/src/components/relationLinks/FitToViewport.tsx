import {type ReactNode, useContext, useLayoutEffect, useRef, useState} from "react";
import {KeptInViewportContext, VIEWPORT_MARGIN} from "../layout/viewportPlacement";

const MIN_HEIGHT = 220;
const OVERLAY_SELECTOR = ".p-overlaypanel";

/**
 * The height left for the element: inside an overlay that keeps itself in the window, the window's height less
 * what the overlay shows above the element; elsewhere, the room left below the element's top edge.
 */
function roomFor(element: HTMLElement, keptInViewport: boolean): number {
  const top = element.getBoundingClientRect().top;
  if (!keptInViewport) return window.innerHeight - top - VIEWPORT_MARGIN;
  const overlayTop = element.closest(OVERLAY_SELECTOR)?.getBoundingClientRect().top ?? top;
  return window.innerHeight - 2 * VIEWPORT_MARGIN - (top - overlayTop);
}

/**
 * Keeps what it holds inside the window: its height is capped to the room it has, and it scrolls past that.
 * Inside an overlay that keeps itself in the window (AnchoredFormOverlay), that room is the window's height, the
 * overlay moving up when it grows; elsewhere an overlay is placed once, when it opens, so the room is what is
 * left below its top edge, and a form that grows afterwards (a list unfolding) scrolls instead of running off
 * the bottom of the screen.
 */
export function FitToViewport({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const keptInViewport = useContext(KeptInViewportContext);
  const [maxHeight, setMaxHeight] = useState<number | undefined>(undefined);

  useLayoutEffect(() => {
    const fit = () => {
      if (ref.current) setMaxHeight(Math.max(MIN_HEIGHT, roomFor(ref.current, keptInViewport)));
    };
    fit();
    window.addEventListener("resize", fit);
    // The overlay is placed after this mounts, and its content grows: measure again each time it changes.
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(fit);
    if (ref.current) observer?.observe(ref.current);
    return () => {
      window.removeEventListener("resize", fit);
      observer?.disconnect();
    };
  }, [keptInViewport]);

  return <div ref={ref} className="sia-graph-fit" style={{ maxHeight }}>{children}</div>;
}
