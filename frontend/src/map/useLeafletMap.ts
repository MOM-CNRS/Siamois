import {type RefObject, useEffect, useState} from "react";
import type L from "leaflet";
import {createBaseMap} from "./leafletMap";

/**
 * The Leaflet map living in the container for as long as the component is mounted (null before).
 * Leaflet measures its container once: the map is told whenever the container changes size (the
 * overview pane opening beside it, the splitter, the window), keeping its centre.
 */
export function useLeafletMap(containerRef: RefObject<HTMLElement>): L.Map | null {
  const [map, setMap] = useState<L.Map | null>(null);
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const created = createBaseMap(container);
    setMap(created);
    const observer = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(() => created.invalidateSize());
    observer?.observe(container);
    return () => {
      observer?.disconnect();
      created.remove();
      setMap(null);
    };
  }, [containerRef]);
  return map;
}
