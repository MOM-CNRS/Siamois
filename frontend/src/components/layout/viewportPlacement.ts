import {createContext} from "react";

// The room kept free between an overlay and the edges of the window.
export const VIEWPORT_MARGIN = 16;
// The gap between an overlay and its anchor: the room PrimeReact's OverlayPanel leaves for its arrow.
const ANCHOR_GAP = 10;

/**
 * Where an overlay sits relative to its anchor: under it, over it, or neither, pulled back into the window
 * when it fits on neither side (it then covers the anchor and loses its arrow).
 */
export type PlacementSide = "below" | "above" | "detached";

export interface VerticalPlacement {
  /** The overlay's top edge, in window coordinates. */
  top: number;
  side: PlacementSide;
}

/** The vertical edges of the overlay's anchor, in window coordinates (a DOMRect fits). */
export interface AnchorEdges {
  top: number;
  bottom: number;
}

/**
 * Places an overlay of the given height next to its anchor so that it stays entirely inside the window:
 * under the anchor when it fits there, over it otherwise, and failing both, as low as the window allows
 * without running off its bottom edge (never above its top edge).
 */
export function placeVertically(anchor: AnchorEdges, height: number, viewportHeight: number): VerticalPlacement {
  const below = anchor.bottom + ANCHOR_GAP;
  if (below + height <= viewportHeight - VIEWPORT_MARGIN) return { top: below, side: "below" };
  const above = anchor.top - ANCHOR_GAP - height;
  if (above >= VIEWPORT_MARGIN) return { top: above, side: "above" };
  return { top: Math.max(VIEWPORT_MARGIN, viewportHeight - VIEWPORT_MARGIN - height), side: "detached" };
}

// True inside an overlay that keeps itself inside the window whatever its height (AnchoredFormOverlay): what it
// holds may then be as tall as the window, instead of only the room left below where the overlay opened.
export const KeptInViewportContext = createContext(false);
