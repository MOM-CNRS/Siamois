import {useEffect, useState} from "react";
import L from "leaflet";
import {addVertex, closeRing, type DraftMode, emptyDraft, type LngLat, type ShapeDraft, undo} from "./shapeDraft";

// The draft's look: dashed, in the theme colour (main-panel.css), so it reads as not yet saved.
const DRAFT_CLASS = "entity-map-draft";
const DRAWING_CLASS = "entity-map-drawing";

/**
 * Draws a geometry on the map one click at a time (shapeDraft), for as long as the component is
 * mounted: polygons, each closed before the next, or a single point. Double-click zooming is off
 * meanwhile (a quick second click is a vertex, not a zoom). Changing the mode starts over.
 */
export function useShapeDraft(map: L.Map | null, initialMode: DraftMode) {
  const [draft, setDraft] = useState<ShapeDraft>(() => emptyDraft(initialMode));

  useEffect(() => {
    if (!map) return;
    const container = map.getContainer();
    container.classList.add(DRAWING_CLASS);
    map.doubleClickZoom.disable();
    const onClick = (e: L.LeafletMouseEvent) => setDraft((current) => addVertex(current, [e.latlng.lng, e.latlng.lat]));
    map.on("click", onClick);
    return () => {
      map.off("click", onClick);
      map.doubleClickZoom.enable();
      container.classList.remove(DRAWING_CLASS);
    };
  }, [map]);

  useEffect(() => {
    if (!map) return;
    const layer = draftLayer(draft).addTo(map);
    return () => {
      layer.remove();
    };
  }, [map, draft]);

  return {
    draft,
    setMode: (mode: DraftMode) => setDraft((current) => (current.mode === mode ? current : emptyDraft(mode))),
    closeRing: () => setDraft(closeRing),
    undo: () => setDraft(undo),
    clear: () => setDraft((current) => emptyDraft(current.mode)),
  };
}

const OPTIONS = { className: DRAFT_CLASS, weight: 2, interactive: false };

// The closed polygons, the ring so far with its vertices, or the point.
function draftLayer(draft: ShapeDraft): L.LayerGroup {
  const toLatLng = ([lng, lat]: LngLat) => L.latLng(lat, lng);
  const layers: L.Layer[] = draft.polygons.map((ring) => L.polygon(ring.map(toLatLng), { ...OPTIONS, fillOpacity: 0.25 }));
  const ring = draft.ring.map(toLatLng);
  if (ring.length >= 3) layers.push(L.polygon(ring, { ...OPTIONS, fillOpacity: 0.15 }));
  else if (ring.length > 0) layers.push(L.polyline(ring, OPTIONS));
  ring.forEach((latLng) => layers.push(L.circleMarker(latLng, { ...OPTIONS, radius: 4, fillOpacity: 1 })));
  if (draft.point) layers.push(L.circleMarker(toLatLng(draft.point), { ...OPTIONS, radius: 7, fillOpacity: 0.8 }));
  return L.layerGroup(layers);
}
