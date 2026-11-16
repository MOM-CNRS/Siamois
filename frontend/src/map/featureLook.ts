import type {CircleMarkerOptions, PathOptions} from "leaflet";
import type {MapFeature} from "../entities/shared/listApi";

// How a set of features is drawn. A look is a constant: the layer hook redraws when it changes.
export interface FeatureLook {
  weight: number;
  // Points are circles: markers would need Leaflet's own icon images, which a bundler breaks.
  radius: number;
  fillOpacity: number;
  // Clicks and tooltips: an overlay that only highlights lets them through to the feature below.
  interactive: boolean;
  className?: string;
}

export const LIST_LOOK: FeatureLook = { weight: 2, radius: 6, fillOpacity: 0.35, interactive: true };

// The fiche open in the overview: thicker, on top, outlined in the theme colour (main-panel.css).
export const SELECTED_LOOK: FeatureLook = { weight: 4, radius: 9, fillOpacity: 0.55, interactive: false, className: "entity-map-selected" };

// The other units around a shape being drawn: faint and inert, there only to draw next to them.
export const CONTEXT_LOOK: FeatureLook = { weight: 1, radius: 4, fillOpacity: 0.15, interactive: false };

// One colour per type label, stable across renders: a hue from the label's hash.
export function typeColor(label: string | null | undefined): string {
  if (!label) return "#6c757d";
  let hash = 0;
  for (const ch of label) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return `hsl(${hash % 360}, 65%, 45%)`;
}

/** The Leaflet options of one feature in a look: its type's colour, the look's strokes. */
export function featureOptions(feature: MapFeature | undefined, look: FeatureLook): PathOptions & CircleMarkerOptions {
  const color = typeColor(feature?.type?.resolvedLabel);
  return {
    color,
    fillColor: color,
    weight: look.weight,
    radius: look.radius,
    fillOpacity: look.fillOpacity,
    interactive: look.interactive,
    ...(look.className ? { className: look.className } : {}),
  };
}
