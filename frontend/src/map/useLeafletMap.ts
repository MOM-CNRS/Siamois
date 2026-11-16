import {type RefObject, useEffect, useState} from "react";
import type L from "leaflet";
import {createBaseMap} from "./leafletMap";

/** The Leaflet map living in the container for as long as the component is mounted (null before). */
export function useLeafletMap(containerRef: RefObject<HTMLElement>): L.Map | null {
  const [map, setMap] = useState<L.Map | null>(null);
  useEffect(() => {
    if (!containerRef.current) return;
    const created = createBaseMap(containerRef.current);
    setMap(created);
    return () => {
      created.remove();
      setMap(null);
    };
  }, [containerRef]);
  return map;
}
